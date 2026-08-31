package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.messaging.AbstractStageWorker;
import dev.shubham.transcoder.messaging.ErrorClassifier;
import dev.shubham.transcoder.messaging.QueueNames;
import dev.shubham.transcoder.messaging.TranscodeTask;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stage-2 listener (transport only) — the horizontally scaled pool
 * ({@code --scale transcode-worker=N}). Prefetch=1 spreads the backlog evenly. The
 * ack/retry/dead-letter skeleton is inherited from {@link AbstractStageWorker}; this class
 * only binds the queue and delegates to {@link TranscodeHandler}.
 */
@Component
public class TranscodeListener extends AbstractStageWorker<TranscodeTask> {

    private final TranscodeHandler transcodeHandler;

    public TranscodeListener(ErrorClassifier errorClassifier, TranscodeHandler transcodeHandler) {
        super(errorClassifier);
        this.transcodeHandler = transcodeHandler;
    }

    @RabbitListener(queues = QueueNames.TRANSCODE_QUEUE)
    public void onTranscode(TranscodeTask task) {
        execute(task);
    }

    @Override
    protected void process(TranscodeTask task) {
        transcodeHandler.transcode(task);
    }

    @Override
    protected String stageName() {
        return "transcode";
    }
}
