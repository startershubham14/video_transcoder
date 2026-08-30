package dev.shubham.transcoder.messaging;

import java.util.UUID;

/**
 * Stage-2 control message: one per segment fanned out by the prepare stage. Idempotent —
 * a redelivery re-encodes to the same deterministic output key.
 *
 * @param jobId     owning job
 * @param segmentId the segment row to transcode
 * @param rung      target resolution rung, e.g. {@code 720p}
 */
public record TranscodeTask(UUID jobId, UUID segmentId, String rung) {
}
