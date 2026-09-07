package dev.shubham.transcoder.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Strongly-typed binding for the {@code pipeline.*} tuning knobs (all env-driven).
 * See {@code docs/architecture-and-workflow.md} §"Config knobs".
 *
 * <p>Validated at startup ({@link Validated}): a bad env value (e.g. an empty
 * {@code RETRY_BACKOFF_SECONDS}, which would otherwise break backoff selection) fails fast with a
 * clear message rather than surfacing as a runtime error mid-pipeline.
 *
 * @param outputMode            {@code mp4} (milestone) or {@code hls} (goal)
 * @param segmentTargetSeconds  approximate keyframe-snapped segment length
 * @param maxDurationSeconds    authoritative duration cap enforced after ffprobe
 * @param maxSizeBytes          authoritative size cap enforced after ffprobe
 * @param retryMaxAttempts      redeliveries before a task is dead-lettered
 * @param retryBackoffSeconds   per-attempt backoff, e.g. {@code [2, 8, 30]}
 * @param inFlightJobCap        admission-control ceiling on concurrent jobs
 * @param uploadDeadlineMinutes grace period before an un-completed upload EXPIRES
 * @param downloadUrlTtlMinutes lifetime of presigned GET URLs minted for output delivery
 * @param reconciliationStaleSeconds age a committed job/segment must reach before the
 *                                   reconciliation sweep re-drives it (avoids racing live work)
 */
@ConfigurationProperties(prefix = "pipeline")
@Validated
public record PipelineProperties(
        @NotBlank String outputMode,
        @Positive int segmentTargetSeconds,
        @Positive int maxDurationSeconds,
        @Positive long maxSizeBytes,
        @Min(0) int retryMaxAttempts,
        @NotEmpty List<@Positive Integer> retryBackoffSeconds,
        @Positive int inFlightJobCap,
        @Positive int uploadDeadlineMinutes,
        @Positive int downloadUrlTtlMinutes,
        @Positive int reconciliationStaleSeconds
) {
}
