package dev.shubham.transcoder.messaging;

import org.springframework.stereotype.Component;

/**
 * Thin publisher for the three task types. Callers follow the enqueue-after-commit rule:
 * commit the state change to Postgres first, then publish here. A lost publish is
 * recovered by the reconciliation sweep, and workers are idempotent, so a duplicate is
 * harmless.
 */
@Component
public class TaskPublisher {

    public void publishPrepare(PrepareTask task) {
        // TODO send to the prepare exchange/routing key.
        throw new UnsupportedOperationException("not implemented");
    }

    public void publishTranscode(TranscodeTask task) {
        // TODO send to the transcode exchange/routing key.
        throw new UnsupportedOperationException("not implemented");
    }

    public void publishPackage(PackageTask task) {
        // TODO send to the concat exchange/routing key.
        throw new UnsupportedOperationException("not implemented");
    }
}
