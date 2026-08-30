package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.messaging.QueueNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stage-3 consumer. Triggered once per rung by the worker that won the fan-in claim.
 * Delegates to {@link PackagingService}; the listener owns ack/nack and retry routing.
 */
@Component
public class PackageWorker {

    private final PackagingService packagingService;

    public PackageWorker(PackagingService packagingService) {
        this.packagingService = packagingService;
    }

    @RabbitListener(queues = QueueNames.CONCAT_QUEUE)
    public void onPackage(PackageTask task) {
        // TODO invoke packagingService.packageRung(task); ack/nack + retry classification.
        throw new UnsupportedOperationException("not implemented");
    }
}
