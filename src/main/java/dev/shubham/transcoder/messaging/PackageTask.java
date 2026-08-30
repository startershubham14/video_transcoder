package dev.shubham.transcoder.messaging;

import java.util.UUID;

/**
 * Stage-3 control message: published exactly once per rung by the worker that wins the
 * atomic fan-in claim. Triggers concat-to-MP4 or package-to-HLS for that rung.
 *
 * @param jobId owning job
 * @param rung  the rung whose segments are all DONE and ready to stitch
 */
public record PackageTask(UUID jobId, String rung) {
}
