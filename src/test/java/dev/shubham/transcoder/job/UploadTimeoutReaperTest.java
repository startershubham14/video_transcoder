package dev.shubham.transcoder.job;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the upload-timeout reaper: past-deadline AWAITING_UPLOAD jobs are marked EXPIRED
 * (no S3 abort — uploadId isn't persisted; S3 lifecycle handles it).
 */
class UploadTimeoutReaperTest {

    private static UploadTimeoutReaper reaper(JobRepository repo) {
        return new UploadTimeoutReaper(repo, mock(PlatformTransactionManager.class));
    }

    @Test
    void marksPastDeadlineJobsExpired() {
        JobRepository repo = mock(JobRepository.class);
        Job job = mock(Job.class);
        UUID id = UUID.randomUUID();
        when(job.getId()).thenReturn(id);
        when(job.getStatus()).thenReturn(JobStatus.AWAITING_UPLOAD);
        when(repo.findByStatusAndUploadDeadlineBefore(eq(JobStatus.AWAITING_UPLOAD), any(Instant.class)))
                .thenReturn(List.of(job));
        when(repo.findById(id)).thenReturn(Optional.of(job));

        reaper(repo).reapExpiredUploads();

        verify(job).markExpired();
    }

    @Test
    void doesNothingWhenNoneStale() {
        JobRepository repo = mock(JobRepository.class);
        when(repo.findByStatusAndUploadDeadlineBefore(eq(JobStatus.AWAITING_UPLOAD), any(Instant.class)))
                .thenReturn(List.of());

        reaper(repo).reapExpiredUploads();

        verify(repo, never()).findById(any());
    }

    @Test
    void skipsIfNoLongerAwaitingUpload() {
        JobRepository repo = mock(JobRepository.class);
        Job job = mock(Job.class);
        UUID id = UUID.randomUUID();
        when(job.getId()).thenReturn(id);
        when(repo.findByStatusAndUploadDeadlineBefore(eq(JobStatus.AWAITING_UPLOAD), any(Instant.class)))
                .thenReturn(List.of(job));
        // re-read under the transaction shows it already progressed
        Job reread = mock(Job.class);
        when(reread.getStatus()).thenReturn(JobStatus.PREPARING);
        when(repo.findById(id)).thenReturn(Optional.of(reread));

        reaper(repo).reapExpiredUploads();

        verify(reread, never()).markExpired();
    }
}
