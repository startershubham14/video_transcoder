package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.transcode.Rung;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic test of ladder derivation: every rung strictly below the source, no upscaling.
 */
class FixedLadderPolicyTest {

    private final LadderPolicy policy = new FixedLadderPolicy();

    @Test
    void fullLadderFrom1080p() {
        assertEquals(List.of(Rung.R720P, Rung.R480P, Rung.R360P), policy.rungsFor(1080));
    }

    @Test
    void noSameResolutionRung() {
        // a 720p source drops the 720 rung (not strictly below), per the design doc example
        assertEquals(List.of(Rung.R480P, Rung.R360P), policy.rungsFor(720));
        assertEquals(List.of(Rung.R360P), policy.rungsFor(480));
    }

    @Test
    void nothingBelowSmallestRung() {
        assertEquals(List.of(), policy.rungsFor(360));
    }
}
