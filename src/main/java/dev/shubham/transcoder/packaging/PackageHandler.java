package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.messaging.JobEventPublisher;
import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.Segment;
import dev.shubham.transcoder.transcode.SegmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stage-3 orchestration + state for one rung: download that rung's encoded segments, delegate
 * to the {@link Packager} resolved from {@code pipeline.output-mode}, then — once every rung's
 * output exists — flip the job to {@code COMPLETED} via a guarded update (job-completion fan-in).
 *
 * <p>Resolves the strategy through {@link PackagerFactory}; never branches on the mode itself.
 * Idempotent: deterministic output keys (redelivery overwrites), and the completion check +
 * guarded {@code tryComplete} fire once even if two rungs finish concurrently. Transport is
 * {@link PackageListener}.
 */
@Service
public class PackageHandler {

    private static final Logger log = LoggerFactory.getLogger(PackageHandler.class);

    private final PackagerFactory packagerFactory;
    private final PipelineProperties pipelineProperties;
    private final BlobStore blobStore;
    private final SegmentRepository segmentRepository;
    private final JobRepository jobRepository;
    private final JobEventPublisher jobEventPublisher;
    private final TransactionTemplate transactionTemplate;

    public PackageHandler(PackagerFactory packagerFactory,
                          PipelineProperties pipelineProperties,
                          BlobStore blobStore,
                          SegmentRepository segmentRepository,
                          JobRepository jobRepository,
                          JobEventPublisher jobEventPublisher,
                          PlatformTransactionManager transactionManager) {
        this.packagerFactory = packagerFactory;
        this.pipelineProperties = pipelineProperties;
        this.blobStore = blobStore;
        this.segmentRepository = segmentRepository;
        this.jobRepository = jobRepository;
        this.jobEventPublisher = jobEventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void packageRung(PackageTask task) {
        UUID jobId = task.jobId();
        String rung = task.rung();
        OutputMode mode = OutputMode.fromConfig(pipelineProperties.outputMode());
        Packager packager = packagerFactory.forMode(mode);

        List<Segment> segments = segmentRepository.findByJobIdAndRungOrderBySegmentIndexAsc(jobId, rung);
        if (segments.isEmpty()) {
            log.warn("[package] job {} rung {} has no segments; skipping", jobId, rung);
            return;
        }

        Path workDir;
        try {
            workDir = Files.createTempDirectory("package-" + jobId + "-" + rung);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // infrastructure → dead-letter
        }
        try {
            List<Path> localSegments = new ArrayList<>(segments.size());
            for (Segment segment : segments) {
                Path file = workDir.resolve(segment.getSegmentIndex() + ".ts");
                blobStore.download(segment.getOutputSegmentKey(), file);
                localSegments.add(file);
            }
            packager.packageRung(jobId, rung, localSegments); // produces + uploads the rung output
        } finally {
            deleteRecursively(workDir);
        }

        completeIfAllRungsPackaged(jobId, packager);
    }

    /** Job-completion fan-in: when every rung's output exists, flip CONCATENATING → COMPLETED once. */
    private void completeIfAllRungsPackaged(UUID jobId, Packager packager) {
        List<String> rungs = segmentRepository.findDistinctRungs(jobId);
        boolean allPackaged = rungs.stream().allMatch(r -> blobStore.exists(packager.outputKey(jobId, r)));
        if (!allPackaged) {
            return;
        }
        packager.finalizeJob(jobId, rungs); // MP4: no-op; HLS: write master manifest
        transactionTemplate.executeWithoutResult(status -> {
            if (jobRepository.tryComplete(jobId) == 1) {
                log.info("[package] job {} COMPLETED ({} rungs)", jobId, rungs.size());
            }
        });
        jobEventPublisher.publish(jobId); // notify SSE watchers: job → COMPLETED
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
