package dev.shubham.transcoder.prepare;

import java.math.BigDecimal;

/**
 * Authoritative source metadata parsed from ffprobe. Drives the input-limit gate and the
 * derived output ladder (no upscaling above the source resolution).
 */
public record ProbeResult(
        int width,
        int height,
        BigDecimal durationSeconds,
        BigDecimal fps,
        String codec,
        long sizeBytes
) {
}
