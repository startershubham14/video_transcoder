package dev.shubham.transcoder.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Broadcasts {@link JobEvent} pokes to {@link QueueNames#JOB_EVENTS_EXCHANGE} (fanout) so every API
 * instance can push a live status snapshot to connected SSE clients. Separate from
 * {@link TaskPublisher}, which routes stage <em>work</em>; this only carries notifications.
 *
 * <p>Called <b>after</b> the state commit (enqueue-after-commit, Golden rule 3). A lost notification
 * is harmless — the API reads authoritative state from Postgres, and clients re-sync on reconnect.
 */
@Component
public class JobEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public JobEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(UUID jobId) {
        rabbitTemplate.convertAndSend(QueueNames.JOB_EVENTS_EXCHANGE, "", new JobEvent(jobId));
    }
}
