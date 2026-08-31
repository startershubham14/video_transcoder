package dev.shubham.transcoder.config;

import dev.shubham.transcoder.messaging.QueueNames;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the topology of the three-stage pipeline: the {@code prepare}, {@code transcode}
 * and {@code concat} work queues, plus the reliability plumbing — a terminal
 * {@code dead-letter} queue and a {@code retry.delay} queue for TTL backoff. Spring Boot's
 * auto-configured {@code RabbitAdmin} declares these {@link Queue} beans on startup, so the
 * broker topology exists before any listener starts.
 *
 * <p>Names live in {@link QueueNames}. The stage queues dead-letter to
 * {@link QueueNames#DEAD_LETTER_QUEUE} via the default exchange; producers route by queue
 * name (default-exchange routing), so no custom exchange/bindings are needed yet.
 */
@Configuration
public class RabbitMqConfig {

    /** Terminal destination for permanent failures / exhausted retries. */
    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(QueueNames.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Queue prepareQueue() {
        return stageQueue(QueueNames.PREPARE_QUEUE);
    }

    @Bean
    Queue transcodeQueue() {
        return stageQueue(QueueNames.TRANSCODE_QUEUE);
    }

    @Bean
    Queue concatQueue() {
        return stageQueue(QueueNames.CONCAT_QUEUE);
    }

    /**
     * Transient failures are republished here with a per-message TTL; on expiry they
     * dead-letter back to their source stage queue.
     *
     * <p>TODO: the retry publisher sets the per-message TTL (2s→8s→30s backoff) and the
     * per-message dead-letter routing key back to the originating stage; declared plain here.
     */
    @Bean
    Queue retryDelayQueue() {
        return QueueBuilder.durable(QueueNames.RETRY_DELAY_QUEUE).build();
    }

    /** Durable stage queue that dead-letters (on nack/reject) to the dead-letter queue. */
    private static Queue stageQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")                               // default exchange
                .deadLetterRoutingKey(QueueNames.DEAD_LETTER_QUEUE)   // → dead-letter.queue
                .build();
    }

    /** Task records travel as JSON referencing S3 keys — never payload bytes. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
