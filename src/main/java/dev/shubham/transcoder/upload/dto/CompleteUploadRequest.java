package dev.shubham.transcoder.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * {@code POST /jobs/{id}/complete} body. The per-part ETags let S3's
 * {@code CompleteMultipartUpload} validate that every part is present and intact —
 * that call, not this request, is the authoritative success signal.
 *
 * @param uploadId  the multipart upload id from {@code /uploads}
 * @param partETags ETags returned by S3 for each uploaded part, in order
 */
public record CompleteUploadRequest(
        @NotBlank String uploadId,
        @NotEmpty List<String> partETags
) {
}
