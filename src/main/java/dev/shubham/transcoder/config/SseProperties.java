package dev.shubham.transcoder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding for the {@code sse.*} knobs (env-driven). Kept separate from {@link PipelineProperties}
 * so the SSE-delivery concern stays isolated.
 *
 * @param timeoutMinutes how long an idle SSE connection is held before it is closed
 */
@ConfigurationProperties(prefix = "sse")
public record SseProperties(long timeoutMinutes) {
}
