package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.messaging.TranscodeTask;
import dev.shubham.transcoder.packaging.PackagerFactory;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.Segment;
import dev.shubham.transcoder.transcode.SegmentRepository;
import dev.shubham.transcoder.transcode.SegmentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;

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
 * Unit test for the reconciliation sweep: stale committed work (PREPARING jobs, QUEUED segments,
 * and stuck CONCATENATING jobs missing a rung output) is re-published; nothing runs when clean.
 */
class ReconciliationSweepTest {

    private static PipelineProperties props() {
        return new PipelineProperties("mp4", 8, 300, 2_147_483_648L, 3, List.of(2, 8, 30), 50, 60, 60, 120);
    }

    private final JobRepository jobRepo = mock(JobRepository.class);
    private final SegmentRepository segRepo = mock(SegmentRepository.class);
    private final TaskPublisher publisher = mock(TaskPublisher.class);
    private final PackagerFactory packagerFactory = mock(PackagerFactory.class);
    private final BlobStore blobStore = mock(BlobStore.class);

    private ReconciliationSweep sweep() {
        return new ReconciliationSweep(jobRepo, segRepo, publisher, props(), packagerFactory, blobStore,
                mock(PlatformTransactionManager.class));
    }

    private static Segment segment(UUID jobId, String rung, SegmentStatus status) {
        Segment s = mock(Segment.class);
        when(s.getJobId()).thenReturn(jobId);
        when(s.getRung()).thenReturn(rung);
        when(s.getStatus()).thenReturn(status);
        return s;
    }

    @Test
    void republishesStalePrepareAndTranscodeWork() {
        UUID jobId = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(jobId);
        when(job.getSourceKey()).thenReturn(jobId + "/source.mp4");
        when(jobRepo.findByStatusAndUpdatedAtBefore(eq(JobStatus.PREPARING), any(Instant.class)))
                .thenReturn(List.of(job));

        UUID segId = UUID.randomUUID();
        Segment queued = mock(Segment.class);
        when(queued.getId()).thenReturn(segId);
        when(queued.getJobId()).thenReturn(jobId);
        when(queued.getRung()).thenReturn("720p");
        when(segRepo.findByStatusAndUpdatedAtBefore(eq(SegmentStatus.QUEUED), any(Instant.class)))
                .thenReturn(List.of(queued));

        sweep().sweep();

        verify(publisher).publishPrepare(new PrepareTask(jobId, jobId + "/source.mp4"));
        verify(publisher).publishTranscode(new TranscodeTask(jobId, segId, "720p"));
    }

    @Test
    void republishesPackageForStuckConcatenatingRungMissingOutput() {
        UUID jobId = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(jobId);
        when(jobRepo.findByStatusAndUpdatedAtBefore(eq(JobStatus.CONCATENATING), any(Instant.class)))
                .thenReturn(List.of(job));
        when(segRepo.findDistinctRungs(jobId)).thenReturn(List.of("720p"));
        Segment done720 = segment(jobId, "720p", SegmentStatus.DONE);
        when(segRepo.findByJobIdAndRung(jobId, "720p")).thenReturn(List.of(done720));

        var packager = mock(dev.shubham.transcoder.packaging.Packager.class);
        when(packager.outputKey(jobId, "720p")).thenReturn(jobId + "/720p.mp4");
        when(packagerFactory.forMode(any())).thenReturn(packager);
        when(blobStore.exists(jobId + "/720p.mp4")).thenReturn(false); // output missing → re-drive
        when(segRepo.tryClaimPackaging(jobId, "720p")).thenReturn(1); // re-claim succeeds

        sweep().sweep();

        verify(publisher).publishPackage(new PackageTask(jobId, "720p"));
    }

    @Test
    void recoversLostClaimForStuckProcessingJob() {
        // The narrow lost-claim edge: job still PROCESSING though a rung is fully DONE with no output.
        UUID jobId = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(jobId);
        when(jobRepo.findByStatusAndUpdatedAtBefore(eq(JobStatus.PROCESSING), any(Instant.class)))
                .thenReturn(List.of(job));
        when(segRepo.findDistinctRungs(jobId)).thenReturn(List.of("720p"));
        Segment done720 = segment(jobId, "720p", SegmentStatus.DONE);
        when(segRepo.findByJobIdAndRung(jobId, "720p")).thenReturn(List.of(done720));

        var packager = mock(dev.shubham.transcoder.packaging.Packager.class);
        when(packager.outputKey(jobId, "720p")).thenReturn(jobId + "/720p.mp4");
        when(packagerFactory.forMode(any())).thenReturn(packager);
        when(blobStore.exists(jobId + "/720p.mp4")).thenReturn(false);
        when(segRepo.tryClaimPackaging(jobId, "720p")).thenReturn(1); // flips PROCESSING→CONCATENATING

        sweep().sweep();

        verify(segRepo).tryClaimPackaging(jobId, "720p");
        verify(publisher).publishPackage(new PackageTask(jobId, "720p"));
    }

    @Test
    void skipsConcatenatingRungThatIsNotAllDoneOrAlreadyPackaged() {
        UUID jobId = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(jobId);
        when(jobRepo.findByStatusAndUpdatedAtBefore(eq(JobStatus.CONCATENATING), any(Instant.class)))
                .thenReturn(List.of(job));
        when(segRepo.findDistinctRungs(jobId)).thenReturn(List.of("720p", "480p"));
        // 720p not all DONE → skip; 480p all DONE but output already exists → skip
        Segment processing720 = segment(jobId, "720p", SegmentStatus.PROCESSING);
        Segment done480 = segment(jobId, "480p", SegmentStatus.DONE);
        when(segRepo.findByJobIdAndRung(jobId, "720p")).thenReturn(List.of(processing720));
        when(segRepo.findByJobIdAndRung(jobId, "480p")).thenReturn(List.of(done480));

        var packager = mock(dev.shubham.transcoder.packaging.Packager.class);
        when(packager.outputKey(jobId, "480p")).thenReturn(jobId + "/480p.mp4");
        when(packagerFactory.forMode(any())).thenReturn(packager);
        when(blobStore.exists(jobId + "/480p.mp4")).thenReturn(true);

        sweep().sweep();

        verify(publisher, Mockito.never()).publishPackage(any());
    }

    @Test
    void doesNothingWhenNoStaleWork() {
        when(jobRepo.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());
        when(segRepo.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        sweep().sweep();

        verifyNoInteractions(publisher);
    }
}
