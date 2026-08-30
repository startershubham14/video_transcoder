package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.messaging.AbstractStageWorker;
import dev.shubham.transcoder.messaging.ErrorClassifier;
import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.messaging.QueueNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stage-3 consumer. Triggered once per rung by the worker that won the fan-in claim. The
 * ack/retry/dead-letter skeleton is inherited from {@link AbstractStageWorker}; this class
 * only binds the queue and supplies the stage body.
 */
@Component
public class PackageWorker extends AbstractStageWorker<PackageTask> {

    private final PackagingService packagingService;

    public PackageWorker(ErrorClassifier errorClassifier, PackagingService packagingService) {
        super(errorClassifier);
        this.packagingService = packagingService;
    }

    @RabbitListener(queues = QueueNames.CONCAT_QUEUE)
    public void onPackage(PackageTask task) {
        execute(task);
    }

    @Override
    protected void process(PackageTask task) {
        packagingService.packageRung(task);
    }

    @Override
    protected String stageName() {
        return "package";
    }
}
