package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.messaging.AbstractStageWorker;
import dev.shubham.transcoder.messaging.ErrorClassifier;
import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.messaging.QueueNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stage-3 listener (transport only). Triggered once per rung by the worker that won the fan-in
 * claim. The ack/retry/dead-letter skeleton is inherited from {@link AbstractStageWorker};
 * this class only binds the queue and delegates to {@link PackageHandler}.
 */
@Component
public class PackageListener extends AbstractStageWorker<PackageTask> {

    private final PackageHandler packageHandler;

    public PackageListener(ErrorClassifier errorClassifier, PackageHandler packageHandler) {
        super(errorClassifier);
        this.packageHandler = packageHandler;
    }

    @RabbitListener(queues = QueueNames.CONCAT_QUEUE)
    public void onPackage(PackageTask task) {
        execute(task);
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
