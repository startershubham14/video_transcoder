package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.messaging.AbstractStageWorker;
import dev.shubham.transcoder.messaging.ErrorClassifier;
import com.rabbitmq.client.Channel;
import dev.shubham.transcoder.messaging.QueueNames;
import dev.shubham.transcoder.messaging.RetryPublisher;
import dev.shubham.transcoder.messaging.TranscodeTask;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Stage-2 listener (transport only) — the horizontally scaled pool
 * ({@code --scale transcode-worker=N}). Prefetch=1 spreads the backlog evenly. The
 * ack/retry/dead-letter skeleton is inherited from {@link AbstractStageWorker}; this class
 * only binds the queue and delegates to {@link TranscodeHandler}. Active in the
 * {@code transcode} profile (its own pool, isolated from prepare).
 */
@Component
@Profile("transcode")
public class TranscodeListener extends AbstractStageWorker<TranscodeTask> {

    private final TranscodeHandler transcodeHandler;

    public TranscodeListener(ErrorClassifier errorClassifier,
                             RetryPublisher retryPublisher,
                             PipelineProperties pipelineProperties,
                             TranscodeHandler transcodeHandler) {
        super(errorClassifier, retryPublisher, pipelineProperties, QueueNames.TRANSCODE_QUEUE);
        this.transcodeHandler = transcodeHandler;
    }

    @RabbitListener(queues = QueueNames.TRANSCODE_QUEUE)
    public void onTranscode(TranscodeTask task, Message message, Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        execute(task, message, channel, deliveryTag);
    }

    @Override
    protected void process(TranscodeTask task) {
        transcodeHandler.transcode(task);
    }

    @Override
    protected String stageName() {
        return "transcode";
    }

    /**
     * A segment that exhausts its retries (or fails permanently) is dead-lettered here. Mark the
     * segment FAILED and fail the owning job so it doesn't hang in PROCESSING (doc §3/§4).
     */
    @Override
    protected void onGiveUp(TranscodeTask task, Throwable cause) {
        transcodeHandler.failSegment(task, cause.getMessage());
    }
}
