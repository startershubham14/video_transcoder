package dev.shubham.transcoder.messaging;

/**
 * Central registry of RabbitMQ queue / exchange / routing-key names so producers,
 * listeners and {@link dev.shubham.transcoder.config.RabbitMqConfig} agree on a single
 * source of truth.
 */
public final class QueueNames {

    private QueueNames() {
    }

    public static final String PREPARE_QUEUE = "prepare.queue";
    public static final String TRANSCODE_QUEUE = "transcode.queue";
    public static final String CONCAT_QUEUE = "concat.queue";

    /** Transient failures park here with a per-message TTL, then dead-letter back. */
    public static final String RETRY_DELAY_QUEUE = "retry.delay.queue";

    /**
     * Fanout exchange feeding {@link #RETRY_DELAY_QUEUE}. Publishing here with the origin stage
     * queue as the routing key parks the message in the retry queue (fanout ignores the key for
     * delivery) while <em>retaining</em> that key, so on TTL-expiry the message dead-letters via
     * the default exchange straight back to the originating stage queue.
     */
    public static final String RETRY_EXCHANGE = "retry.exchange";

    /** Terminal destination for permanent failures / exhausted retries. */
    public static final String DEAD_LETTER_QUEUE = "dead-letter.queue";
}
