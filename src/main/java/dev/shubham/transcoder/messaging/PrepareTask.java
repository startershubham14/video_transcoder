package dev.shubham.transcoder.messaging;

import java.util.UUID;

/**
 * Stage-1 control message: "prepare the source for this job." Carries only references —
 * the bytes stay in S3.
 *
 * @param jobId     the job to prepare
 * @param sourceKey S3 object key of the uploaded source, e.g. {@code {jobId}/source.mp4}
 */
public record PrepareTask(UUID jobId, String sourceKey) {
}
