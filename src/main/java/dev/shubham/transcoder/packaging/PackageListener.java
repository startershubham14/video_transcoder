package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.messaging.AbstractStageWorker;
import dev.shubham.transcoder.messaging.ErrorClassifier;
import com.rabbitmq.client.Channel;
import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.messaging.QueueNames;
import dev.shubham.transcoder.messaging.RetryPublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Stage-3 listener (transport only). Triggered once per rung by the worker that won the fan-in
 * claim. The ack/retry/dead-letter skeleton is inherited from {@link AbstractStageWorker};
 * this class only binds the queue and delegates to {@link PackageHandler}. Active in the
 * {@code worker} profile (shares the prepare pool).
 */
@Component
@Profile("worker")
public class PackageListener extends AbstractStageWorker<PackageTask> {

    private final PackageHandler packageHandler;

    public PackageListener(ErrorClassifier errorClassifier,
                           RetryPublisher retryPublisher,
                           PipelineProperties pipelineProperties,
                           PackageHandler packageHandler) {
        super(errorClassifier, retryPublisher, pipelineProperties, QueueNames.CONCAT_QUEUE);
        this.packageHandler = packageHandler;
    }

    @RabbitListener(queues = QueueNames.CONCAT_QUEUE)
    public void onPackage(PackageTask task, Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        execute(task, message, channel, deliveryTag);
    }

    @Override
    protected void process(PackageTask task) {
        packageHandler.packageRung(task);
    }

    @Override
    protected String stageName() {
        return "package";
    }
}
