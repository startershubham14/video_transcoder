package dev.shubham.transcoder.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin publisher for the three task types. Sends each task to its stage queue via the default
 * exchange (routing key = queue name); the configured JSON converter serializes the record.
 * Callers follow the enqueue-after-commit rule: commit the state change to Postgres first,
 * then publish here. A lost publish is recovered by the reconciliation sweep, and workers are
 * idempotent, so a duplicate is harmless.
 */
@Component
public class TaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPrepare(PrepareTask task) {
        rabbitTemplate.convertAndSend(QueueNames.PREPARE_QUEUE, task);
    }

    public void publishTranscode(TranscodeTask task) {
        rabbitTemplate.convertAndSend(QueueNames.TRANSCODE_QUEUE, task);
    }

    public void publishPackage(PackageTask task) {
        rabbitTemplate.convertAndSend(QueueNames.CONCAT_QUEUE, task);
    }
}
