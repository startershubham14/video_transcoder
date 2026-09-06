package dev.shubham.transcoder.config;

import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.job.JobStatus;
import dev.shubham.transcoder.messaging.QueueNames;
import dev.shubham.transcoder.transcode.SegmentRepository;
import dev.shubham.transcoder.transcode.SegmentStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the pipeline gauges read live counts from Postgres (mocked repos) and the broker (mocked
 * RabbitAdmin), and that a missing queue reports 0.
 */
class PipelineMetricsTest {

    @Test
    void gaugesReportRepoAndBrokerCounts() {
        MeterRegistry registry = new SimpleMeterRegistry();
        JobRepository jobRepository = mock(JobRepository.class);
        SegmentRepository segmentRepository = mock(SegmentRepository.class);
        RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);

        when(jobRepository.countByStatus(JobStatus.PROCESSING)).thenReturn(3L);
        when(segmentRepository.countByStatus(SegmentStatus.DONE)).thenReturn(10L);
        when(rabbitAdmin.getQueueInfo(QueueNames.TRANSCODE_QUEUE))
                .thenReturn(new QueueInformation(QueueNames.TRANSCODE_QUEUE, 5, 2));
        when(rabbitAdmin.getQueueInfo(QueueNames.DEAD_LETTER_QUEUE)).thenReturn(null); // not declared

        new PipelineMetrics(registry, jobRepository, segmentRepository, rabbitAdmin);

        assertEquals(3.0, gauge(registry, "pipeline.jobs", "status", JobStatus.PROCESSING.name()));
        assertEquals(10.0, gauge(registry, "pipeline.segments", "status", SegmentStatus.DONE.name()));
        assertEquals(5.0, gauge(registry, "pipeline.queue.depth", "queue", QueueNames.TRANSCODE_QUEUE));
        assertEquals(0.0, gauge(registry, "pipeline.queue.depth", "queue", QueueNames.DEAD_LETTER_QUEUE));
    }

    private static double gauge(MeterRegistry registry, String name, String tagKey, String tagValue) {
        return registry.get(name).tag(tagKey, tagValue).gauge().value();
    }
}
