package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.messaging.AbstractStageWorker;
import dev.shubham.transcoder.messaging.ErrorClassifier;
import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.QueueNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stage-1 listener (transport only). Runs as a small, fixed pool kept separate from the
 * transcode pool so a transcode backlog can't starve incoming jobs (head-of-line-blocking
 * fix). The ack/retry/dead-letter skeleton is inherited from {@link AbstractStageWorker};
 * this class only binds the queue and delegates to {@link PrepareHandler}. Active in the
 * {@code worker} profile.
 */
@Component
@Profile("worker")
public class PrepareListener extends AbstractStageWorker<PrepareTask> {

    private final PrepareHandler prepareHandler;

    public PrepareListener(ErrorClassifier errorClassifier, PrepareHandler prepareHandler) {
        super(errorClassifier);
        this.prepareHandler = prepareHandler;
    }

    @RabbitListener(queues = QueueNames.PREPARE_QUEUE)
    public void onPrepare(PrepareTask task) {
        execute(task);
    }

    @Override
    protected void process(PrepareTask task) {
        prepareHandler.prepare(task);
    }

    @Override
    protected String stageName() {
        return "prepare";
    }
}
