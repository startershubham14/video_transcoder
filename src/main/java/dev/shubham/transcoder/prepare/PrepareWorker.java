package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.QueueNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stage-1 consumer. Runs as a small, fixed pool kept separate from the transcode pool so
 * a transcode backlog can't starve incoming jobs (head-of-line-blocking fix). Delegates
 * to {@link PrepareService}; the listener only handles ack/nack and retry classification.
 */
@Component
public class PrepareWorker {

    private final PrepareService prepareService;

    public PrepareWorker(PrepareService prepareService) {
        this.prepareService = prepareService;
    }

    @RabbitListener(queues = QueueNames.PREPARE_QUEUE)
    public void onPrepare(PrepareTask task) {
        // TODO invoke prepareService.prepare(task); ack on success, route failures to
        // TODO retry-delay (transient) or dead-letter (permanent).
        throw new UnsupportedOperationException("not implemented");
    }
}
