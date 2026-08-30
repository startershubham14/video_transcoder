package dev.shubham.transcoder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Strongly-typed binding for the {@code pipeline.*} tuning knobs (all env-driven).
 * See {@code docs/architecture-and-workflow.md} §"Config knobs".
 *
 * @param outputMode            {@code mp4} (milestone) or {@code hls} (goal)
 * @param segmentTargetSeconds  approximate keyframe-snapped segment length
 * @param maxDurationSeconds    authoritative duration cap enforced after ffprobe
 * @param maxSizeBytes          authoritative size cap enforced after ffprobe
 * @param retryMaxAttempts      redeliveries before a task is dead-lettered
 * @param retryBackoffSeconds   per-attempt backoff, e.g. {@code [2, 8, 30]}
 * @param inFlightJobCap        admission-control ceiling on concurrent jobs
 * @param uploadDeadlineMinutes grace period before an un-completed upload EXPIRES
 */
@ConfigurationProperties(prefix = "pipeline")
public record PipelineProperties(
        String outputMode,
        int segmentTargetSeconds,
        int maxDurationSeconds,
        long maxSizeBytes,
        int retryMaxAttempts,
        List<Integer> retryBackoffSeconds,
        int inFlightJobCap,
        int uploadDeadlineMinutes
) {
}
