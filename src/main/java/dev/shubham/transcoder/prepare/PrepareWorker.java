package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.messaging.AbstractStageWorker;
import dev.shubham.transcoder.messaging.ErrorClassifier;
import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.QueueNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stage-1 consumer. Runs as a small, fixed pool kept separate from the transcode pool so
 * a transcode backlog can't starve incoming jobs (head-of-line-blocking fix). The
 * ack/retry/dead-letter skeleton is inherited from {@link AbstractStageWorker}; this class
 * only binds the queue and supplies the stage body.
 */
@Component
public class PrepareWorker extends AbstractStageWorker<PrepareTask> {

    private final PrepareService prepareService;

    public PrepareWorker(ErrorClassifier errorClassifier, PrepareService prepareService) {
        super(errorClassifier);
        this.prepareService = prepareService;
    }

    @RabbitListener(queues = QueueNames.PREPARE_QUEUE)
    public void onPrepare(PrepareTask task) {
        execute(task);
    }

    @Override
    protected void process(PrepareTask task) {
        prepareService.prepare(task);
    }

    @Override
    protected String stageName() {
        return "prepare";
    }
}
