package dev.shubham.transcoder.prepare;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure-logic test of the post-ffprobe input-limit gate.
 */
class PrepareLimitsTest {

    private static final int MAX_DURATION = 300;          // 5 min
    private static final long MAX_SIZE = 2_147_483_648L;  // ~2 GB

    private static ProbeResult probe(int seconds, long bytes) {
        return new ProbeResult(1920, 1080, BigDecimal.valueOf(seconds), new BigDecimal("30"), "h264", bytes);
    }

    @Test
    void acceptsWithinLimits() {
        assertDoesNotThrow(() -> PrepareHandler.enforceLimits(probe(120, 1_000_000L), MAX_DURATION, MAX_SIZE));
    }

    @Test
    void rejectsOverDuration() {
        assertThrows(PrepareRejectedException.class,
                () -> PrepareHandler.enforceLimits(probe(301, 1_000_000L), MAX_DURATION, MAX_SIZE));
    }

    @Test
    void rejectsOverSize() {
        assertThrows(PrepareRejectedException.class,
                () -> PrepareHandler.enforceLimits(probe(120, MAX_SIZE + 1), MAX_DURATION, MAX_SIZE));
    }
}
