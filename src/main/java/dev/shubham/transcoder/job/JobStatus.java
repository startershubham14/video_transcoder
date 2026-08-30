package dev.shubham.transcoder.job;

/**
 * Overall job lifecycle, driven by the three pipeline stages.
 * See the job state machine in {@code docs/architecture-and-workflow.md} §3.
 */
public enum JobStatus {
    AWAITING_UPLOAD,
    PREPARING,
    PROCESSING,
    CONCATENATING,
    COMPLETED,
    FAILED,
    EXPIRED
}
