package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.job.Job;
import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.job.JobStatus;
import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.messaging.TranscodeTask;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.Rung;
import dev.shubham.transcoder.transcode.Segment;
import dev.shubham.transcoder.transcode.SegmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stage-1 orchestration + state: download source → ffprobe → enforce input limits → ClamAV
 * scan → derive the output ladder → keyframe-aligned split → upload source segments → (in one
 * transaction) insert segment rows per rung + commit {@code PROCESSING} → fan out transcode
 * tasks after the commit.
 *
 * <p>Long I/O runs outside any DB transaction; only the segment inserts + status change are
 * transactional (short), and the fan-out publishes after commit (enqueue-after-commit). A
 * permanent, input-caused failure ({@link PrepareRejectedException}) fails the job and is not
 * retried; infrastructure exceptions propagate to be dead-lettered. Redelivery-safe: a job not
 * in {@code PREPARING} is skipped. Transport is {@link PrepareListener}.
 */
@Service
public class PrepareHandler {

    private static final Logger log = LoggerFactory.getLogger(PrepareHandler.class);

    private final BlobStore blobStore;
    private final MediaProbe mediaProbe;
    private final VirusScanner virusScanner;
    private final Splitter splitter;
    private final LadderPolicy ladderPolicy;
    private final JobRepository jobRepository;
    private final SegmentRepository segmentRepository;
    private final TaskPublisher taskPublisher;
    private final PipelineProperties pipelineProperties;
    private final TransactionTemplate transactionTemplate;

    public PrepareHandler(BlobStore blobStore,
                          MediaProbe mediaProbe,
                          VirusScanner virusScanner,
                          Splitter splitter,
                          LadderPolicy ladderPolicy,
                          JobRepository jobRepository,
                          SegmentRepository segmentRepository,
                          TaskPublisher taskPublisher,
                          PipelineProperties pipelineProperties,
                          PlatformTransactionManager transactionManager) {
        this.blobStore = blobStore;
        this.mediaProbe = mediaProbe;
        this.virusScanner = virusScanner;
        this.splitter = splitter;
        this.ladderPolicy = ladderPolicy;
        this.jobRepository = jobRepository;
        this.segmentRepository = segmentRepository;
        this.taskPublisher = taskPublisher;
        this.pipelineProperties = pipelineProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void prepare(PrepareTask task) {
        Job job = jobRepository.findById(task.jobId()).orElse(null);
        if (job == null) {
            log.warn("[prepare] job {} not found; nothing to prepare", task.jobId());
            return;
        }
        if (job.getStatus() != JobStatus.PREPARING) {
            log.info("[prepare] job {} already handled (status {}); skipping", task.jobId(), job.getStatus());
            return; // idempotent: a redelivery does not re-run
        }

        Path workDir;
        try {
            workDir = Files.createTempDirectory("prepare-" + task.jobId());
        } catch (IOException e) {
            throw new UncheckedIOException(e); // infrastructure → dead-letter
        }

        try {
            Path source = workDir.resolve("source");
            blobStore.download(task.sourceKey(), source);

            ProbeResult probe;
            List<Rung> rungs;
            List<Path> segmentFiles;
            try {
                probe = mediaProbe.probe(source);
                enforceLimits(probe, pipelineProperties.maxDurationSeconds(), pipelineProperties.maxSizeBytes());
                if (!virusScanner.isClean(source)) {
                    throw new PrepareRejectedException("infected");
                }
                rungs = ladderPolicy.rungsFor(probe.height());
                if (rungs.isEmpty()) {
                    throw new PrepareRejectedException("source resolution too low for any output rung");
                }
                segmentFiles = splitter.split(source, pipelineProperties.segmentTargetSeconds());
                if (segmentFiles.isEmpty()) {
                    throw new PrepareRejectedException("split produced no segments");
                }
            } catch (PrepareRejectedException rejected) {
                log.warn("[prepare] job {} rejected: {}", task.jobId(), rejected.getMessage());
                failJob(task.jobId(), rejected.getMessage());
                return; // terminal — ack, no retry
            }

            List<String> segmentKeys = new ArrayList<>(segmentFiles.size());
            for (int index = 0; index < segmentFiles.size(); index++) {
                String key = task.jobId() + "/segments/" + index + ".ts"; // shared by all rungs
                blobStore.upload(segmentFiles.get(index), key);
                segmentKeys.add(key);
            }

            persistAndFanOut(task.jobId(), probe, rungs, segmentKeys);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /** Insert segment rows per rung, flip to PROCESSING, and fan out transcode tasks after commit. */
    private void persistAndFanOut(UUID jobId, ProbeResult probe, List<Rung> rungs, List<String> segmentKeys) {
        transactionTemplate.executeWithoutResult(status -> {
            Job job = jobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() != JobStatus.PREPARING) {
                return; // re-check under the transaction (redelivery-safe)
            }
            job.recordProbe(probe.width(), probe.height(), probe.durationSeconds(),
                    probe.fps(), probe.codec(), probe.sizeBytes());

            List<Segment> segments = new ArrayList<>(rungs.size() * segmentKeys.size());
            for (Rung rung : rungs) {
                for (int index = 0; index < segmentKeys.size(); index++) {
                    segments.add(Segment.create(jobId, rung, index, segmentKeys.get(index)));
                }
            }
            List<Segment> saved = segmentRepository.saveAll(segments);
            job.markProcessing(); // PREPARING -> PROCESSING

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Segment segment : saved) {
                        taskPublisher.publishTranscode(
                                new TranscodeTask(jobId, segment.getId(), segment.getRung()));
                    }
                }
            });
        });
    }

    private void failJob(UUID jobId, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            Job job = jobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() == JobStatus.PREPARING) {
                job.failWith(reason); // PREPARING -> FAILED
            }
        });
    }

    /** Authoritative post-ffprobe limit gate. Package-visible for unit testing. */
    static void enforceLimits(ProbeResult probe, int maxDurationSeconds, long maxSizeBytes) {
        if (probe.durationSeconds().compareTo(BigDecimal.valueOf(maxDurationSeconds)) > 0) {
            throw new PrepareRejectedException("duration exceeds MAX_DURATION_SECONDS");
        }
        if (probe.sizeBytes() > maxSizeBytes) {
            throw new PrepareRejectedException("size exceeds MAX_SIZE_BYTES");
        }
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort scratch cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort scratch cleanup
        }
    }
}
