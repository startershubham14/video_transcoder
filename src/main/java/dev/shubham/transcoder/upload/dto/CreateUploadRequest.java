package dev.shubham.transcoder.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * {@code POST /uploads} body. {@code sizeBytes} is the client-declared size — an early,
 * cheap (and untrusted) gate; the authoritative size/duration check happens after
 * ffprobe in the prepare stage.
 */
public record CreateUploadRequest(
        @NotBlank String filename,
        @Positive long sizeBytes,
        @NotBlank String contentType
) {
}
