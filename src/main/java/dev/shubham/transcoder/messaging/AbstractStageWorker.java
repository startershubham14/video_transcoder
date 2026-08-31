package dev.shubham.transcoder.messaging;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Template Method for the three stage consumers (prepare / transcode / package). Fixes the
 * invariant skeleton — run the stage body, ack on success, and route failures uniformly — so
 * reliability logic is written once, not re-implemented (and allowed to diverge) across three
 * workers. Subclasses supply only {@link #process} and {@link #stageName}.
 *
 * @param <T> the task message type for this stage
 */
public abstract class AbstractStageWorker<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractStageWorker.class);

    private final ErrorClassifier errorClassifier;

    protected AbstractStageWorker(ErrorClassifier errorClassifier) {
        this.errorClassifier = errorClassifier;
    }

    /**
     * Run {@link #process}; on success ack, on failure nack (no requeue) so the message
     * dead-letters via the stage queue's DLX. Manual ack means we ack only after the work and
     * any state commit have completed.
     *
     * <p>TODO (reliability step): use {@link #errorClassifier} to route TRANSIENT failures to
     * the retry-delay queue with backoff instead of dead-lettering immediately.
     */
    protected final void execute(T task, Channel channel, long deliveryTag) {
        try {
            process(task);
        } catch (Exception e) {
            log.error("[{}] task failed, dead-lettering: {}", stageName(), e.toString(), e);
            nack(channel, deliveryTag);
            return;
        }
        ack(channel, deliveryTag);
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
}
