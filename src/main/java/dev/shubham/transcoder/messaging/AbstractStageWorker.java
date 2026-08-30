package dev.shubham.transcoder.messaging;

/**
 * Template Method for the three stage consumers (prepare / transcode / package). Fixes the
 * invariant skeleton — run the stage body, and on failure classify and route retry-vs-
 * dead-letter uniformly — so that reliability logic is written once, not re-implemented
 * (and allowed to diverge) across three workers. Subclasses supply only {@link #process}.
 *
 * @param <T> the task message type for this stage
 */
public abstract class AbstractStageWorker<T> {

    private final ErrorClassifier errorClassifier;

    protected AbstractStageWorker(ErrorClassifier errorClassifier) {
        this.errorClassifier = errorClassifier;
    }

    /**
     * Invariant handling: attempt {@link #process}, ack on success; on failure use
     * {@link ErrorClassifier} to route to the retry-delay queue (transient) or the
     * dead-letter queue (permanent / attempts exhausted).
     */
    protected final void execute(T task) {
        // TODO try { process(task); ack; } catch (Throwable e) {
        // TODO   switch (errorClassifier.classify(e)) { TRANSIENT -> retry-delay; PERMANENT -> DLQ; }
        // TODO }  — requires the Channel + delivery tag for manual ack.
        throw new UnsupportedOperationException("not implemented");
    }

    /** The stage-specific body. */
    protected abstract void process(T task) throws Exception;

    /** Short stage name for logging / metrics. */
    protected abstract String stageName();
}
