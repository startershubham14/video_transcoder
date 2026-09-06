package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.messaging.JobEventPublisher;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.messaging.TranscodeTask;
import dev.shubham.transcoder.storage.BlobStore;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the transcode handler's terminal/retry state writes (mocked repos; the
 * TransactionTemplate runs the callback synchronously against a mock transaction manager).
 */
class TranscodeHandlerTest {

    private final Transcoder transcoder = mock(Transcoder.class);
    private final BlobStore blobStore = mock(BlobStore.class);
    private final SegmentRepository segmentRepository = mock(SegmentRepository.class);
    private final JobRepository jobRepository = mock(JobRepository.class);
    private final TaskPublisher taskPublisher = mock(TaskPublisher.class);
    private final JobEventPublisher jobEventPublisher = mock(JobEventPublisher.class);

    private TranscodeHandler handler() {
        return new TranscodeHandler(transcoder, blobStore, segmentRepository, jobRepository,
                taskPublisher, jobEventPublisher, mock(PlatformTransactionManager.class));
    }

    @Test
    void markSegmentRetryWaitParksTheSegment() {
        UUID segmentId = UUID.randomUUID();
        handler().markSegmentRetryWait(segmentId);
        verify(segmentRepository).markRetryWait(segmentId);
    }

    @Test
    void failSegmentMarksSegmentAndJobFailedAndNotifies() {
        UUID jobId = UUID.randomUUID();
        UUID segmentId = UUID.randomUUID();
        TranscodeTask task = new TranscodeTask(jobId, segmentId, "720p");
        when(jobRepository.failJob(eq(jobId), eq("boom"))).thenReturn(1);

        handler().failSegment(task, "boom");

        verify(segmentRepository).markFailed(segmentId);
        verify(jobRepository).failJob(jobId, "boom");
        verify(jobEventPublisher).publish(jobId);
    }
}
