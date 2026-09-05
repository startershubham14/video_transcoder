package dev.shubham.transcoder.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Parks a transiently-failed message for a backoff delay before it returns to its stage queue.
 * RabbitMQ has no native redelivery delay, so the message is re-published to
 * {@link QueueNames#RETRY_EXCHANGE} (fanout → {@code retry.delay.queue}) with a per-message TTL;
 * on expiry the delay queue dead-letters it via the default exchange using its retained routing
 * key — the origin stage queue — so it comes back for another attempt. The attempt count rides
 * along in {@link #RETRY_ATTEMPTS_HEADER}.
 *
 * <p>The original message <em>bytes</em> are re-published unchanged (no re-serialization), so a
 * retried task is byte-identical to the original — idempotent workers handle the redelivery.
 */
@Component
public class RetryPublisher {

    /** Header carrying how many retry attempts a message has already been scheduled for. */
    public static final String RETRY_ATTEMPTS_HEADER = "x-retry-attempts";

    private final RabbitTemplate rabbitTemplate;

    public RetryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Re-publish {@code original} to the retry exchange with a delay, tagged with the next attempt.
     *
     * @param original    the failed inbound message
     * @param originQueue the stage queue to return to after the delay (used as the routing key)
     * @param nextAttempt the attempt number this retry represents (1-based)
     * @param ttlMs       backoff delay in milliseconds
     */
    public void scheduleRetry(Message original, String originQueue, int nextAttempt, long ttlMs) {
        MessageProperties props = MessagePropertiesBuilder
                .fromClonedProperties(original.getMessageProperties())
                .setExpiration(Long.toString(ttlMs))
                .setHeader(RETRY_ATTEMPTS_HEADER, nextAttempt)
                .build();
        Message copy = new Message(original.getBody(), props);
        rabbitTemplate.send(QueueNames.RETRY_EXCHANGE, originQueue, copy);
    }
}
