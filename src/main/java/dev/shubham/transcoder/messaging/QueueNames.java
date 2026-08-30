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

    /** Terminal destination for permanent failures / exhausted retries. */
    public static final String DEAD_LETTER_QUEUE = "dead-letter.queue";
}
