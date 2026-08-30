package dev.shubham.transcoder.job.dto;

import dev.shubham.transcoder.job.JobStatus;

import java.util.List;
import java.util.UUID;

/**
 * Response for {@code GET /jobs/{id}}. Progress is derived from segment states; output
 * URLs are freshly minted presigned GETs, present only once COMPLETED.
 *
 * @param jobId        the job
 * @param status       current lifecycle status
 * @param progress     0–100, derived from DONE / total segments
 * @param errorReason  populated when FAILED / EXPIRED
 * @param outputUrls   presigned download URLs (empty until COMPLETED)
 */
public record JobStatusResponse(
        UUID jobId,
        JobStatus status,
        int progress,
        String errorReason,
        List<String> outputUrls
) {
}
