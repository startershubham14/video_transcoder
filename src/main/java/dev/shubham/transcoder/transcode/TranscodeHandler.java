package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.messaging.TranscodeTask;
import dev.shubham.transcoder.storage.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stage-2 orchestration + state for one segment: mark PROCESSING → download → FFmpeg encode →
 * upload under a deterministic key → mark DONE and run the atomic per-rung fan-in claim
 * (Golden rule 5). Exactly one worker per rung wins {@link SegmentRepository#tryClaimPackaging}
 * and enqueues the {@link PackageTask} for that rung after commit.
 *
 * <p>Idempotent: deterministic output key (redelivery overwrites); an already-DONE segment
 * skips the encode and just re-attempts the claim (recovery if a prior run died before
 * claiming). Infra exceptions propagate to be dead-lettered. Transport is {@link TranscodeListener}.
 */
@Service
public class TranscodeHandler {

    private static final Logger log = LoggerFactory.getLogger(TranscodeHandler.class);

    private final Transcoder transcoder;
    private final BlobStore blobStore;
    private final SegmentRepository segmentRepository;
    private final JobRepository jobRepository;
    private final TaskPublisher taskPublisher;
    private final TransactionTemplate transactionTemplate;

    public TranscodeHandler(Transcoder transcoder,
                            BlobStore blobStore,
                            SegmentRepository segmentRepository,
                            JobRepository jobRepository,
                            TaskPublisher taskPublisher,
                            PlatformTransactionManager transactionManager) {
        this.transcoder = transcoder;
        this.blobStore = blobStore;
        this.segmentRepository = segmentRepository;
        this.jobRepository = jobRepository;
        this.taskPublisher = taskPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void transcode(TranscodeTask task) {
        Segment segment = segmentRepository.findById(task.segmentId()).orElse(null);
        if (segment == null) {
            log.warn("[transcode] segment {} not found; nothing to do", task.segmentId());
            return;
        }
        UUID jobId = segment.getJobId();
        String rung = segment.getRung();

        if (segment.getStatus() == SegmentStatus.DONE) {
            // Already encoded; make sure the fan-in ran (a prior run may have died before claiming).
            claim(jobId, rung);
            return;
        }

        Path workDir;
        try {
            workDir = Files.createTempDirectory("transcode-" + task.segmentId());
        } catch (IOException e) {
            throw new UncheckedIOException(e); // infrastructure → dead-letter
        }
        try {
            transactionTemplate.executeWithoutResult(status -> segmentRepository.markProcessing(task.segmentId()));

            Path source = workDir.resolve("segment.ts");
            blobStore.download(segment.getSourceSegmentKey(), source);
            Path encoded = transcoder.encode(source, Rung.fromLabel(rung));

            String outputKey = outputKey(jobId, rung, segment.getSegmentIndex());
            blobStore.upload(encoded, outputKey);

            finishAndClaim(task.segmentId(), jobId, rung, outputKey);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /** Mark DONE + atomically claim packaging for the rung; publish after commit if we won. */
    private void finishAndClaim(UUID segmentId, UUID jobId, String rung, String outputKey) {
        transactionTemplate.executeWithoutResult(status -> {
            segmentRepository.markDone(segmentId, outputKey);
            if (segmentRepository.tryClaimPackaging(jobId, rung) == 1) {
                publishPackageAfterCommit(jobId, rung);
            }
        });
    }

    /**
     * Terminal give-up for a segment that exhausted its retries (invoked by
     * {@link TranscodeListener#onGiveUp}): mark the segment FAILED and fail the owning job so it
     * doesn't hang in PROCESSING. Guarded + idempotent under concurrent segment failures.
     */
    public void failSegment(TranscodeTask task, String reason) {
        String detail = reason == null || reason.isBlank() ? "transcode failed" : reason;
        transactionTemplate.executeWithoutResult(status -> {
            segmentRepository.markFailed(task.segmentId());
            if (jobRepository.failJob(task.jobId(), detail) == 1) {
                log.warn("[transcode] job {} FAILED: segment {} gave up ({})",
                        task.jobId(), task.segmentId(), detail);
            }
        });
    }

    /** Re-attempt the claim for an already-DONE segment (recovery path). */
    private void claim(UUID jobId, String rung) {
        transactionTemplate.executeWithoutResult(status -> {
            if (segmentRepository.tryClaimPackaging(jobId, rung) == 1) {
                publishPackageAfterCommit(jobId, rung);
            }
        });
    }

    private void publishPackageAfterCommit(UUID jobId, String rung) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskPublisher.publishPackage(new PackageTask(jobId, rung));
            }
        });
    }

    /** Deterministic per-segment encoded-output key. Package-visible for unit testing. */
    static String outputKey(UUID jobId, String rung, int segmentIndex) {
        return jobId + "/" + rung + "/" + segmentIndex + ".ts";
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
