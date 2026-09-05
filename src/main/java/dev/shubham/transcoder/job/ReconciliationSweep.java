package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.messaging.TranscodeTask;
import dev.shubham.transcoder.transcode.Segment;
import dev.shubham.transcoder.transcode.SegmentRepository;
import dev.shubham.transcoder.transcode.SegmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
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

    public ReconciliationSweep(JobRepository jobRepository,
                               SegmentRepository segmentRepository,
                               TaskPublisher taskPublisher,
                               PipelineProperties pipelineProperties) {
        this.jobRepository = jobRepository;
        this.segmentRepository = segmentRepository;
        this.taskPublisher = taskPublisher;
        this.pipelineProperties = pipelineProperties;
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
    }
}
