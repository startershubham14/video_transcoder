package dev.shubham.transcoder.job;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled safety net for the enqueue-after-commit rule. Every hand-off commits state
 * to Postgres first, then publishes to RabbitMQ; a publish that fails leaves a job whose
 * DB state implies work that no message represents. This sweep finds those "committed but
 * never enqueued" jobs/segments and re-publishes. Combined with idempotent workers, a
 * duplicate re-publish is harmless. Runs in the {@code api} profile.
 */
@Component
@Profile("api")
public class ReconciliationSweep {

    @Scheduled(fixedDelayString = "${pipeline.reconciliation-interval-ms:30000}")
    public void sweep() {
        // TODO find stuck states (PREPARING/PROCESSING with no in-flight message) and re-enqueue.
        // No-op until implemented (this runs on a timer).
    }
}
