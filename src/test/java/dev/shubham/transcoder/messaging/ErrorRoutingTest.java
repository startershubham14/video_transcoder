package dev.shubham.transcoder.messaging;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Placeholder for the transient-vs-permanent routing that keeps the queue stable (see
 * {@code docs/architecture-and-workflow.md} §5). Enabled once {@link ErrorClassifier} and
 * {@link AbstractStageWorker}'s retry/dead-letter routing are implemented.
 */
@Disabled("pending implementation of ErrorClassifier + AbstractStageWorker routing")
class ErrorRoutingTest {

    @Test
    void permanentErrorsDeadLetterOnFirstAttempt() {
        // TODO assert a permanent failure routes straight to the DLQ (no retry).
    }

    @Test
    void transientErrorsRetryWithBackoffThenDeadLetter() {
        // TODO assert a transient failure parks in the retry-delay queue and, after
        // TODO RETRY_MAX_ATTEMPTS, dead-letters.
    }
}
