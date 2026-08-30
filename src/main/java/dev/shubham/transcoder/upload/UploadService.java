package dev.shubham.transcoder.upload;

import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.upload.dto.CompleteUploadRequest;
import dev.shubham.transcoder.upload.dto.CreateUploadResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates the thin upload flow. On create: persist a job in {@code AWAITING_UPLOAD}
 * (with an upload deadline) and initiate an S3 multipart upload, returning presigned part
 * URLs. On complete: call {@code CompleteMultipartUpload} (authoritative), verify size via
 * HeadObject, transition to {@code PREPARING}, then — after the DB commit — enqueue the
 * prepare task (enqueue-after-commit).
 */
@Service
public class UploadService {

    private final JobRepository jobRepository;
    private final BlobStore blobStore;
    private final TaskPublisher taskPublisher;

    public UploadService(JobRepository jobRepository, BlobStore blobStore, TaskPublisher taskPublisher) {
        this.jobRepository = jobRepository;
        this.blobStore = blobStore;
        this.taskPublisher = taskPublisher;
    }

    /** Create the job + multipart upload; return presigned part URLs. */
    public CreateUploadResponse createUpload(String filename, long sizeBytes, String contentType) {
        // TODO declared-size gate; persist job; blobStore.initiateMultipartUpload(...); presign parts.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Finalize the upload and enqueue prepare (commit state first, then publish). */
    public void completeUpload(UUID jobId, CompleteUploadRequest request) {
        // TODO blobStore.completeMultipartUpload(...); headObjectSize check; job -> PREPARING;
        // TODO commit, then taskPublisher.publishPrepare(...).
        throw new UnsupportedOperationException("not implemented");
    }
}
