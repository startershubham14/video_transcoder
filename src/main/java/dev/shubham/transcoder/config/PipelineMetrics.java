package dev.shubham.transcoder.config;

import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.job.JobStatus;
import dev.shubham.transcoder.messaging.QueueNames;
import dev.shubham.transcoder.transcode.SegmentRepository;
import dev.shubham.transcoder.transcode.SegmentStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Registers the pipeline's Prometheus gauges, computed <em>on the API</em> from the source of truth
 * (Postgres) and the broker — so workers stay headless and there's no app hot-path cost (the supplier
 * lambdas run only when Prometheus scrapes {@code /actuator/prometheus}). Throughput (jobs/segments per
 * minute) is derived from these series in Grafana via {@code rate()}.
 *
 * <ul>
 *   <li>{@code pipeline.jobs{status}} — jobs per {@link JobStatus}</li>
 *   <li>{@code pipeline.segments{status}} — segments per {@link SegmentStatus}</li>
 *   <li>{@code pipeline.queue.depth{queue}} — ready messages per stage/retry/DLQ queue</li>
 * </ul>
 */
@Component
@Profile("api")
public class PipelineMetrics {

    private static final List<String> QUEUES = List.of(
            QueueNames.PREPARE_QUEUE, QueueNames.TRANSCODE_QUEUE, QueueNames.CONCAT_QUEUE,
            QueueNames.RETRY_DELAY_QUEUE, QueueNames.DEAD_LETTER_QUEUE);

    private final RabbitAdmin rabbitAdmin;

    public PipelineMetrics(MeterRegistry registry,
                           JobRepository jobRepository,
                           SegmentRepository segmentRepository,
                           RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;

        for (JobStatus status : JobStatus.values()) {
            gauge(registry, "pipeline.jobs", "status", status.name(),
                    () -> jobRepository.countByStatus(status));
        }
        for (SegmentStatus status : SegmentStatus.values()) {
            gauge(registry, "pipeline.segments", "status", status.name(),
                    () -> segmentRepository.countByStatus(status));
        }
        for (String queue : QUEUES) {
            gauge(registry, "pipeline.queue.depth", "queue", queue, () -> queueDepth(queue));
        }
    }

    private static void gauge(MeterRegistry registry, String name, String tagKey, String tagValue,
                              Supplier<Number> supplier) {
        Gauge.builder(name, supplier)
                .tag(tagKey, tagValue)
                .register(registry);
    }

    /** Ready-message count for a queue; 0 if the queue isn't declared yet / broker is unreachable. */
    private double queueDepth(String queue) {
        try {
            QueueInformation info = rabbitAdmin.getQueueInfo(queue);
            return info == null ? 0 : info.getMessageCount();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
