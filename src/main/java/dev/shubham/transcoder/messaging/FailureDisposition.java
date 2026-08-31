package dev.shubham.transcoder.messaging;

/**
 * How a stage failure should be routed — the pipeline's highest-leverage reliability
 * decision (see {@code docs/architecture-and-workflow.md} §5).
 */
public enum FailureDisposition {

    /** Retryable (S3 503, timeout): park in the retry-delay queue with backoff. */
    TRANSIENT,

    /** Unrecoverable (corrupt input, missing key): dead-letter on the first attempt. */
    PERMANENT
}
