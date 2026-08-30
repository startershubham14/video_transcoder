package dev.shubham.transcoder.upload;

import dev.shubham.transcoder.upload.dto.CompleteUploadRequest;
import dev.shubham.transcoder.upload.dto.CreateUploadResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates the thin upload flow. On create: persist a job in {@code AWAITING_UPLOAD}
 * (with an upload deadline) and initiate an S3 multipart upload, returning presigned part
 * URLs. On complete: call {@code CompleteMultipartUpload} (authoritative), verify size via
 * HeadObject, transition to {@code PREPARING}, then — after the DB commit — enqueue the
 * prepare task.
 */
@Service
public class UploadService {

    /** Create the job + multipart upload; return presigned part URLs. */
    public CreateUploadResponse createUpload(String filename, long sizeBytes, String contentType) {
        // TODO declared-size gate; persist job; S3 initiate multipart; presign parts.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Finalize the upload and enqueue prepare (commit state first, then publish). */
    public void completeUpload(UUID jobId, CompleteUploadRequest request) {
        // TODO CompleteMultipartUpload; HeadObject size check; job -> PREPARING; publish PrepareTask.
        throw new UnsupportedOperationException("not implemented");
    }
}
