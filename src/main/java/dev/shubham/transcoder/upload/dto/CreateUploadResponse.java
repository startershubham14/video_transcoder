package dev.shubham.transcoder.upload.dto;

import java.util.List;
import java.util.UUID;

/**
 * {@code POST /uploads} response: the new job id plus the presigned part URLs the client
 * PUTs bytes to directly (bytes never touch the API).
 *
 * @param jobId      newly created job (AWAITING_UPLOAD)
 * @param uploadId   S3 multipart upload id, echoed back on complete
 * @param partUrls   presigned PUT URL per part, in order
 * @param expiresInSeconds presigned-URL TTL
 */
public record CreateUploadResponse(
        UUID jobId,
        String uploadId,
        List<String> partUrls,
        long expiresInSeconds
) {
}
