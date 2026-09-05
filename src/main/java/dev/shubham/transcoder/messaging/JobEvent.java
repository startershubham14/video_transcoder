package dev.shubham.transcoder.messaging;

import java.util.UUID;

/**
 * A "job changed" notification broadcast on {@link QueueNames#JOB_EVENTS_EXCHANGE} after a
 * state-affecting DB commit. Intentionally just the {@code jobId}: the API re-reads the current
 * state from Postgres (the source of truth) rather than trusting details carried on the wire.
 *
 * @param jobId the job whose state changed
 */
public record JobEvent(UUID jobId) {
}
