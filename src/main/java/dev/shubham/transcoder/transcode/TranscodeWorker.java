package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.messaging.QueueNames;
import dev.shubham.transcoder.messaging.TranscodeTask;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stage-2 consumer — the horizontally scaled pool ({@code --scale transcode-worker=N}).
 * Prefetch=1 spreads the backlog evenly. Delegates to {@link TranscodeService}; the
 * listener owns ack/nack and transient-vs-permanent retry routing.
 */
@Component
public class TranscodeWorker {

    private final TranscodeService transcodeService;

    public TranscodeWorker(TranscodeService transcodeService) {
        this.transcodeService = transcodeService;
    }

    @RabbitListener(queues = QueueNames.TRANSCODE_QUEUE)
    public void onTranscode(TranscodeTask task) {
        // TODO invoke transcodeService.transcode(task); ack/nack + retry classification.
        throw new UnsupportedOperationException("not implemented");
    }
}
