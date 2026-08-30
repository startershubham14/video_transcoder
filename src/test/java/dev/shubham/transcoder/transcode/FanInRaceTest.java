package dev.shubham.transcoder.transcode;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Placeholder for the pipeline's must-test cases around the per-rung fan-in (see
 * {@code docs/architecture-and-workflow.md} §9). Enabled once the transcode stage and the
 * atomic claim query are implemented — these need a real Postgres (Testcontainers).
 */
@Disabled("pending implementation of the transcode stage + atomic fan-in claim")
class FanInRaceTest {

    @Test
    void exactlyOneWorkerPerRungWinsTheConcatClaim() {
        // TODO concurrently mark the last segments of a rung DONE; assert the guarded
        // TODO UPDATE affects exactly one row (one PackageTask published).
    }

    @Test
    void redeliveredTranscodeTaskIsIdempotent() {
        // TODO replay a TranscodeTask; assert no duplicate output/segment rows and a
        // TODO stable output key.
    }
}
