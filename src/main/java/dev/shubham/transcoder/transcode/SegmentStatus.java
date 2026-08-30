package dev.shubham.transcoder.transcode;

/**
 * Per-segment lifecycle — where the reliability story lives. A crashed worker never
 * acked, so RabbitMQ redelivers (PROCESSING → QUEUED); transient errors wait in the
 * retry-delay queue (RETRY_WAIT); permanent errors or exhausted retries dead-letter
 * (FAILED). See the segment state machine in {@code docs/architecture-and-workflow.md} §4.
 */
public enum SegmentStatus {
    QUEUED,
    PROCESSING,
    DONE,
    RETRY_WAIT,
    FAILED
}
