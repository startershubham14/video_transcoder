package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.messaging.TranscodeTask;
import dev.shubham.transcoder.packaging.OutputMode;
import dev.shubham.transcoder.packaging.Packager;
import dev.shubham.transcoder.packaging.PackagerFactory;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.Segment;
import dev.shubham.transcoder.transcode.SegmentRepository;
import dev.shubham.transcoder.transcode.SegmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled safety net for the enqueue-after-commit rule. Every hand-off commits state
 * to Postgres first, then publishes to RabbitMQ; a publish that fails leaves a job whose
 * DB state implies work that no message represents. This sweep finds those "committed but
 * never enqueued" jobs/segments and re-publishes. Combined with idempotent workers, a
 * duplicate re-publish is harmless. Runs in the {@code api} profile.
 *
 * <p>Only rows untouched for at least {@code pipeline.reconciliation-stale-seconds} are
 * re-driven, so the sweep never races work that is merely in flight.
 */
@Component
@Profile("api")
public class ReconciliationSweep {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationSweep.class);

    private final JobRepository jobRepository;
    private final SegmentRepository segmentRepository;
    private final TaskPublisher taskPublisher;
    private final PipelineProperties pipelineProperties;
    private final PackagerFactory packagerFactory;
    private final BlobStore blobStore;
    private final TransactionTemplate transactionTemplate;

    public ReconciliationSweep(JobRepository jobRepository,
                               SegmentRepository segmentRepository,
                               TaskPublisher taskPublisher,
                               PipelineProperties pipelineProperties,
                               PackagerFactory packagerFactory,
                               BlobStore blobStore,
                               PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.segmentRepository = segmentRepository;
        this.taskPublisher = taskPublisher;
        this.pipelineProperties = pipelineProperties;
        this.packagerFactory = packagerFactory;
        this.blobStore = blobStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${pipeline.reconciliation-interval-ms:30000}")
    public void sweep() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(pipelineProperties.reconciliationStaleSeconds()));

        // Stuck in PREPARING: the prepare task never made it onto the queue.
        List<Job> stalePreparing = jobRepository.findByStatusAndUpdatedAtBefore(JobStatus.PREPARING, cutoff);
        for (Job job : stalePreparing) {
            log.warn("[reconcile] re-publishing prepare for stuck job {}", job.getId());
            taskPublisher.publishPrepare(new PrepareTask(job.getId(), job.getSourceKey()));
        }

        // Stuck QUEUED segments: a fanned-out transcode task was lost. Workers are idempotent,
        // so re-publishing an already-processing/done segment is safe.
        List<Segment> staleQueued = segmentRepository.findByStatusAndUpdatedAtBefore(SegmentStatus.QUEUED, cutoff);
        for (Segment segment : staleQueued) {
            log.warn("[reconcile] re-publishing transcode for stuck segment {} (job {})",
                    segment.getId(), segment.getJobId());
            taskPublisher.publishTranscode(
                    new TranscodeTask(segment.getJobId(), segment.getId(), segment.getRung()));
        }

        // Stuck packaging: a rung is fully DONE but its packaged output is missing. Two causes, both
        // recovered the same way — re-claim then re-publish:
        //   * job still PROCESSING: the fan-in claim itself was lost (a perfectly-simultaneous final
        //     completion where every worker's tryClaim ran before any markDone committed);
        //   * job already CONCATENATING: the claim ran but the PackageTask publish was lost.
        // tryClaimPackaging flips PROCESSING→CONCATENATING (or re-matches CONCATENATING), returning 1 in
        // either recoverable case; the all-DONE guard avoids packaging a partial rung.
        List<Job> stalePackaging = new ArrayList<>();
        stalePackaging.addAll(jobRepository.findByStatusAndUpdatedAtBefore(JobStatus.PROCESSING, cutoff));
        stalePackaging.addAll(jobRepository.findByStatusAndUpdatedAtBefore(JobStatus.CONCATENATING, cutoff));
        if (!stalePackaging.isEmpty()) {
            Packager packager = packagerFactory.forMode(OutputMode.fromConfig(pipelineProperties.outputMode()));
            for (Job job : stalePackaging) {
                for (String rung : segmentRepository.findDistinctRungs(job.getId())) {
                    List<Segment> segments = segmentRepository.findByJobIdAndRung(job.getId(), rung);
                    boolean allDone = !segments.isEmpty()
                            && segments.stream().allMatch(s -> s.getStatus() == SegmentStatus.DONE);
                    if (allDone && !blobStore.exists(packager.outputKey(job.getId(), rung))) {
                        int claimed = transactionTemplate.execute(
                                status -> segmentRepository.tryClaimPackaging(job.getId(), rung));
                        if (claimed == 1) {
                            log.warn("[reconcile] re-claiming + re-publishing package for stuck job {} rung {}",
                                    job.getId(), rung);
                            taskPublisher.publishPackage(new PackageTask(job.getId(), rung));
                        }
                    }
                }
            }
        }
    }
}
