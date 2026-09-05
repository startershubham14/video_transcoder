package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.job.dto.JobStatusResponse;
import dev.shubham.transcoder.packaging.OutputMode;
import dev.shubham.transcoder.packaging.Packager;
import dev.shubham.transcoder.packaging.PackagerFactory;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.SegmentRepository;
import dev.shubham.transcoder.transcode.SegmentStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-logic tests for status assembly (no I/O — repos/BlobStore/factory mocked). Covers the
 * progress derivation edge cases and the "URLs only when COMPLETED" contract.
 */
class JobStatusServiceTest {

    private static PipelineProperties props(String mode) {
        return new PipelineProperties(mode, 8, 300, 2_147_483_648L, 3, List.of(2, 8, 30), 50, 60, 60, 120);
    }

    // ---- computeProgress (pure) ----

    @Test
    void progressIsZeroBeforeFanOut() {
        assertEquals(0, JobStatusService.computeProgress(JobStatus.PREPARING, 0, 0));
    }

    @Test
    void progressIsDoneFractionWhileProcessing() {
        assertEquals(40, JobStatusService.computeProgress(JobStatus.PROCESSING, 2, 5));
    }

    @Test
    void progressCapsAt99UntilCompleted() {
        // all segments done but job still packaging → never show 100
        assertEquals(99, JobStatusService.computeProgress(JobStatus.CONCATENATING, 5, 5));
    }

    @Test
    void progressIs100OnlyWhenCompleted() {
        assertEquals(100, JobStatusService.computeProgress(JobStatus.COMPLETED, 0, 0));
        assertEquals(100, JobStatusService.computeProgress(JobStatus.COMPLETED, 3, 5));
    }

    // ---- getStatus ----

    @Test
    void unknownJobIs404() {
        JobRepository jobs = mock(JobRepository.class);
        UUID id = UUID.randomUUID();
        when(jobs.findById(id)).thenReturn(Optional.empty());

        JobStatusService service = new JobStatusService(
                jobs, mock(SegmentRepository.class), mock(BlobStore.class),
                mock(PackagerFactory.class), props("mp4"));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.getStatus(id));
    }

    @Test
    void processingJobReportsProgressAndNoUrls() {
        UUID id = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(JobStatus.PROCESSING);
        when(job.getErrorReason()).thenReturn(null);

        JobRepository jobs = mock(JobRepository.class);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        SegmentRepository segments = mock(SegmentRepository.class);
        when(segments.countByJobId(id)).thenReturn(4L);
        when(segments.countByJobIdAndStatus(id, SegmentStatus.DONE)).thenReturn(1L);
        BlobStore blobStore = mock(BlobStore.class);

        JobStatusService service = new JobStatusService(
                jobs, segments, blobStore, mock(PackagerFactory.class), props("mp4"));

        JobStatusResponse res = service.getStatus(id);

        assertEquals(JobStatus.PROCESSING, res.status());
        assertEquals(25, res.progress());
        assertTrue(res.outputUrls().isEmpty());
        verify(blobStore, never()).presignGet(any(), any());
    }

    @Test
    void completedMp4JobMintsOneUrlPerRung() throws Exception {
        UUID id = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(id);
        when(job.getStatus()).thenReturn(JobStatus.COMPLETED);
        when(job.getErrorReason()).thenReturn(null);

        JobRepository jobs = mock(JobRepository.class);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        SegmentRepository segments = mock(SegmentRepository.class);
        when(segments.countByJobId(id)).thenReturn(6L);
        when(segments.countByJobIdAndStatus(id, SegmentStatus.DONE)).thenReturn(6L);
        when(segments.findDistinctRungs(id)).thenReturn(List.of("720", "480"));

        Packager mp4 = mock(Packager.class);
        when(mp4.outputKey(id, "720")).thenReturn(id + "/720.mp4");
        when(mp4.outputKey(id, "480")).thenReturn(id + "/480.mp4");
        PackagerFactory factory = mock(PackagerFactory.class);
        when(factory.forMode(OutputMode.MP4)).thenReturn(mp4);

        BlobStore blobStore = mock(BlobStore.class);
        when(blobStore.presignGet(eq(id + "/720.mp4"), any())).thenReturn(url("https://s3.local/720"));
        when(blobStore.presignGet(eq(id + "/480.mp4"), any())).thenReturn(url("https://s3.local/480"));

        JobStatusService service = new JobStatusService(jobs, segments, blobStore, factory, props("mp4"));

        JobStatusResponse res = service.getStatus(id);

        assertEquals(100, res.progress());
        assertEquals(List.of("https://s3.local/720", "https://s3.local/480"), res.outputUrls());
        verify(blobStore).presignGet(eq(id + "/720.mp4"), eq(Duration.ofMinutes(60)));
    }

    @Test
    void failedJobSurfacesErrorReason() {
        UUID id = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.getStatus()).thenReturn(JobStatus.FAILED);
        when(job.getErrorReason()).thenReturn("infected: Eicar-Test-Signature");

        JobRepository jobs = mock(JobRepository.class);
        when(jobs.findById(id)).thenReturn(Optional.of(job));
        SegmentRepository segments = mock(SegmentRepository.class);
        when(segments.countByJobId(id)).thenReturn(0L);
        when(segments.countByJobIdAndStatus(id, SegmentStatus.DONE)).thenReturn(0L);

        JobStatusService service = new JobStatusService(
                jobs, segments, mock(BlobStore.class), mock(PackagerFactory.class), props("mp4"));

        JobStatusResponse res = service.getStatus(id);

        assertEquals(JobStatus.FAILED, res.status());
        assertEquals("infected: Eicar-Test-Signature", res.errorReason());
        assertTrue(res.outputUrls().isEmpty());
    }

    private static URL url(String s) throws Exception {
        return URI.create(s).toURL();
    }
}
