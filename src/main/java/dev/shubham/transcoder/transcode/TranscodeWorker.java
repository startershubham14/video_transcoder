package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.messaging.AbstractStageWorker;
import dev.shubham.transcoder.messaging.ErrorClassifier;
import dev.shubham.transcoder.messaging.QueueNames;
import dev.shubham.transcoder.messaging.TranscodeTask;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stage-2 consumer — the horizontally scaled pool ({@code --scale transcode-worker=N}).
 * Prefetch=1 spreads the backlog evenly. The ack/retry/dead-letter skeleton is inherited
 * from {@link AbstractStageWorker}; this class only binds the queue and supplies the body.
 */
@Component
public class TranscodeWorker extends AbstractStageWorker<TranscodeTask> {

    private final TranscodeService transcodeService;

    public TranscodeWorker(ErrorClassifier errorClassifier, TranscodeService transcodeService) {
        super(errorClassifier);
        this.transcodeService = transcodeService;
    }

    @RabbitListener(queues = QueueNames.TRANSCODE_QUEUE)
    public void onTranscode(TranscodeTask task) {
        execute(task);
    }

    @Override
    protected void process(TranscodeTask task) {
        transcodeService.transcode(task);
    }

    @Override
    protected String stageName() {
        return "transcode";
    }
}
