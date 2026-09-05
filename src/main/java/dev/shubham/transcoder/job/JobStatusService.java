package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.job.dto.JobStatusResponse;
import dev.shubham.transcoder.packaging.OutputMode;
import dev.shubham.transcoder.packaging.Packager;
import dev.shubham.transcoder.packaging.PackagerFactory;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.SegmentRepository;
import dev.shubham.transcoder.transcode.SegmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assembles the job status/progress view from job + segment rows — genuine cross-entity
 * domain logic. State transitions themselves are owned by the stage handlers and the atomic
 * fan-in update. Admission is a separate concern ({@link AdmissionPolicy}).
 *
 * <p>Progress is derived from segment counts (cheap COUNTs, never full-row loads). Output
 * URLs are freshly minted presigned GETs, computed from {@code jobId} + rung via the same
 * {@link Packager#outputKey} the packaging stage uses — never persisted (Golden rule 8) and
 * present only once the job is COMPLETED.
 */
@Service
public class JobStatusService {

    private final JobRepository jobRepository;
    private final SegmentRepository segmentRepository;
    private final BlobStore blobStore;
    private final PackagerFactory packagerFactory;
    private final PipelineProperties pipelineProperties;

    public JobStatusService(JobRepository jobRepository,
                            SegmentRepository segmentRepository,
                            BlobStore blobStore,
                            PackagerFactory packagerFactory,
                            PipelineProperties pipelineProperties) {
        this.jobRepository = jobRepository;
        this.segmentRepository = segmentRepository;
        this.blobStore = blobStore;
        this.packagerFactory = packagerFactory;
        this.pipelineProperties = pipelineProperties;
    }

    /** Build the poll response: status, derived progress, and (if done) output URLs. */
    public JobStatusResponse getStatus(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job " + jobId));

        long total = segmentRepository.countByJobId(jobId);
        long done = segmentRepository.countByJobIdAndStatus(jobId, SegmentStatus.DONE);
        int progress = computeProgress(job.getStatus(), done, total);

        List<String> outputUrls = job.getStatus() == JobStatus.COMPLETED
                ? outputUrls(job)
                : List.of();

        return new JobStatusResponse(jobId, job.getStatus(), progress, job.getErrorReason(), outputUrls);
    }

    /**
     * Derive 0–100 progress from segment counts. {@code COMPLETED} is authoritatively 100;
     * before fan-out ({@code total == 0}) it is 0; otherwise it is the DONE fraction, capped
     * at 99 so "100%" never shows while the job is still CONCATENATING/packaging.
     */
    static int computeProgress(JobStatus status, long done, long total) {
        if (status == JobStatus.COMPLETED) {
            return 100;
        }
        if (total <= 0) {
            return 0;
        }
        int pct = (int) (100 * done / total);
        return Math.min(99, pct);
    }

    /**
     * URL(s) for the job's outputs. A mode with a single job-level artifact (HLS: the master
     * {@code .m3u8}) delivers that one URL; otherwise (MP4) one presigned URL per rung. Keys are
     * derived (never stored) via the configured {@link Packager}; callers never branch on
     * {@link OutputMode} themselves.
     *
     * <p>HLS is served as a plain public URL (its manifest references many sibling files a single
     * presigned URL couldn't cover); MP4 stays presigned.
     */
    private List<String> outputUrls(Job job) {
        OutputMode mode = OutputMode.fromConfig(pipelineProperties.outputMode());
        Packager packager = packagerFactory.forMode(mode);

        Optional<String> master = packager.masterOutputKey(job.getId());
        if (master.isPresent()) {
            return List.of(blobStore.publicUrl(master.get()).toString());
        }

        Duration ttl = Duration.ofMinutes(pipelineProperties.downloadUrlTtlMinutes());
        return segmentRepository.findDistinctRungs(job.getId()).stream()
                .map(rung -> packager.outputKey(job.getId(), rung))
                .map(key -> blobStore.presignGet(key, ttl).toString())
                .toList();
    }
}
