package dev.shubham.transcoder.config;

import org.springframework.context.annotation.Configuration;

/**
 * Declares the topology of the three-stage pipeline: the {@code prepare},
 * {@code transcode} and {@code concat} work queues, plus the reliability plumbing —
 * a {@code retry.delay} queue (per-message TTL, dead-lettering back to its source for
 * exponential backoff) and a terminal {@code dead-letter} queue.
 *
 * <p>Queue/exchange/routing-key names live in
 * {@link dev.shubham.transcoder.messaging.QueueNames}.
 *
 * <p>TODO: declare Queue / Exchange / Binding beans, the retry-delay TTL bindings,
 * and a Jackson message converter.
 */
@Configuration
public class RabbitMqConfig {

    // TODO @Bean topology (queues, exchanges, bindings, DLX, retry-delay TTL) and
    // TODO a Jackson2JsonMessageConverter so task records serialize as JSON.
}
