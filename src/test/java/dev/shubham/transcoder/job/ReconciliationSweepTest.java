package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.messaging.TranscodeTask;
import dev.shubham.transcoder.transcode.Segment;
import dev.shubham.transcoder.transcode.SegmentRepository;
import dev.shubham.transcoder.transcode.SegmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test for the reconciliation sweep: stale committed work (PREPARING jobs, QUEUED segments)
 * is re-published; nothing is touched when there is no stale work.
 */
class ReconciliationSweepTest {

    private static PipelineProperties props() {
        return new PipelineProperties("mp4", 8, 300, 2_147_483_648L, 3, List.of(2, 8, 30), 50, 60, 60, 120);
    }

    @Test
    void republishesStalePrepareAndTranscodeWork() {
        JobRepository jobRepo = mock(JobRepository.class);
        SegmentRepository segRepo = mock(SegmentRepository.class);
        TaskPublisher publisher = mock(TaskPublisher.class);

        UUID jobId = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(jobId);
        when(job.getSourceKey()).thenReturn(jobId + "/source.mp4");
        when(jobRepo.findByStatusAndUpdatedAtBefore(eq(JobStatus.PREPARING), any(Instant.class)))
                .thenReturn(List.of(job));

        UUID segId = UUID.randomUUID();
        Segment segment = mock(Segment.class);
        when(segment.getId()).thenReturn(segId);
        when(segment.getJobId()).thenReturn(jobId);
        when(segment.getRung()).thenReturn("720");
        when(segRepo.findByStatusAndUpdatedAtBefore(eq(SegmentStatus.QUEUED), any(Instant.class)))
                .thenReturn(List.of(segment));

        new ReconciliationSweep(jobRepo, segRepo, publisher, props()).sweep();

        verify(publisher).publishPrepare(new PrepareTask(jobId, jobId + "/source.mp4"));
        verify(publisher).publishTranscode(new TranscodeTask(jobId, segId, "720"));
    }

    @Test
    void doesNothingWhenNoStaleWork() {
        JobRepository jobRepo = mock(JobRepository.class);
        SegmentRepository segRepo = mock(SegmentRepository.class);
        TaskPublisher publisher = mock(TaskPublisher.class);
        when(jobRepo.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());
        when(segRepo.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        new ReconciliationSweep(jobRepo, segRepo, publisher, props()).sweep();

        verifyNoInteractions(publisher);
    }
}
