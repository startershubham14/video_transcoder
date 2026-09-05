package dev.shubham.transcoder.messaging;

import com.rabbitmq.client.Channel;
import dev.shubham.transcoder.config.PipelineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.util.List;

/**
 * Template Method for the three stage consumers (prepare / transcode / package). Fixes the
 * invariant skeleton — run the stage body, ack on success, and route failures uniformly — so
 * reliability logic is written once, not re-implemented (and allowed to diverge) across three
 * workers. Subclasses supply only {@link #process}, {@link #stageName}, the origin queue name,
 * and (optionally) an {@link #onGiveUp} hook.
 *
 * <p><b>Failure routing</b> (architecture doc §5): a failure is classified by
 * {@link ErrorClassifier}. A <em>transient</em> failure under the attempt cap is parked in the
 * retry-delay queue with exponential backoff ({@link RetryPublisher}) and the original delivery
 * acked; a <em>permanent</em> failure, or a transient one that has exhausted its retries,
 * dead-letters immediately (nack → the stage queue's DLX → {@code dead-letter.queue}).
 *
 * @param <T> the task message type for this stage
 */
public abstract class AbstractStageWorker<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractStageWorker.class);

    private final ErrorClassifier errorClassifier;
    private final RetryPublisher retryPublisher;
    private final PipelineProperties pipelineProperties;
    private final String originQueue;

    protected AbstractStageWorker(ErrorClassifier errorClassifier,
                                  RetryPublisher retryPublisher,
                                  PipelineProperties pipelineProperties,
                                  String originQueue) {
        this.errorClassifier = errorClassifier;
        this.retryPublisher = retryPublisher;
        this.pipelineProperties = pipelineProperties;
        this.originQueue = originQueue;
    }

    /**
     * Run {@link #process}; on success ack. On failure, route by {@link FailureDisposition}:
     * transient-under-cap → retry-delay queue with backoff (then ack the original); permanent or
     * retries-exhausted → nack (no requeue) so the message dead-letters via the stage queue's DLX.
     * Manual ack means we ack only after the work and any state commit have completed.
     */
    protected final void execute(T task, Message message, Channel channel, long deliveryTag) {
        try {
            process(task);
            ack(channel, deliveryTag);
            return;
        } catch (Exception e) {
            routeFailure(task, message, channel, deliveryTag, e);
        }
    }

    private void routeFailure(T task, Message message, Channel channel, long deliveryTag, Exception e) {
        FailureDisposition disposition = errorClassifier.classify(e);
        int attempts = attemptsSoFar(message);
        int maxAttempts = pipelineProperties.retryMaxAttempts();

        if (disposition == FailureDisposition.TRANSIENT && attempts < maxAttempts) {
            int nextAttempt = attempts + 1;
            long ttlMs = backoffMs(attempts);
            log.warn("[{}] transient failure (attempt {}/{}), retrying in {}ms: {}",
                    stageName(), nextAttempt, maxAttempts, ttlMs, e.toString());
            try {
                retryPublisher.scheduleRetry(message, originQueue, nextAttempt, ttlMs);
                ack(channel, deliveryTag); // original removed; the delayed copy carries the work
            } catch (Exception republishFailure) {
                log.error("[{}] failed to schedule retry, dead-lettering instead", stageName(),
                        republishFailure);
                nack(channel, deliveryTag);
            }
            return;
        }

        log.error("[{}] {} failure, dead-lettering (after {} attempt(s)): {}", stageName(),
                disposition == FailureDisposition.PERMANENT ? "permanent" : "retries-exhausted",
                attempts, e.toString(), e);
        try {
            onGiveUp(task, e);
        } catch (Exception hookFailure) {
            log.error("[{}] onGiveUp hook failed", stageName(), hookFailure);
        }
        nack(channel, deliveryTag);
    }

    /** Backoff for the given (0-based) prior-attempt count, clamped to the configured list. */
    private long backoffMs(int attempts) {
        List<Integer> backoff = pipelineProperties.retryBackoffSeconds();
        int index = Math.min(attempts, backoff.size() - 1);
        return backoff.get(index) * 1000L;
    }

    private static int attemptsSoFar(Message message) {
        Object header = message.getMessageProperties().getHeader(RetryPublisher.RETRY_ATTEMPTS_HEADER);
        return header instanceof Number n ? n.intValue() : 0;
    }

    private void ack(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("[{}] ack failed for delivery {}", stageName(), deliveryTag, e);
        }
    }

    private void nack(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false); // multiple=false, requeue=false → DLX
        } catch (IOException e) {
            log.error("[{}] nack failed for delivery {}", stageName(), deliveryTag, e);
        }
    }

    /** The stage-specific body. */
    protected abstract void process(T task) throws Exception;

    /** Short stage name for logging / metrics. */
    protected abstract String stageName();

    /**
     * Hook invoked just before a task is dead-lettered (permanent failure or retries exhausted).
     * Default no-op; a stage overrides it to record terminal state — e.g. transcode marks the
     * segment FAILED and fails the job so it doesn't hang.
     */
    protected void onGiveUp(T task, Throwable cause) {
        // no-op by default
    }
}
