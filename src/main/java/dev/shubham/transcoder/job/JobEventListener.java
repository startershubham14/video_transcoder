package dev.shubham.transcoder.job;

import com.rabbitmq.client.Channel;
import dev.shubham.transcoder.messaging.JobEvent;
import dev.shubham.transcoder.messaging.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * API-side consumer of {@link JobEvent} pokes. Binds an <em>anonymous, auto-delete</em> queue to the
 * {@code job.events} fanout, so every API instance receives every event and can push to whichever SSE
 * clients it holds ({@link OutputDeliveryService}). Only meaningful in the {@code api} profile.
 *
 * <p>Manual ack (the global acknowledge-mode): the event is acked in a {@code finally} regardless of
 * outcome — a dropped poke is harmless (state is authoritative in Postgres; clients re-sync).
 */
@Component
@Profile("api")
public class JobEventListener {

    private static final Logger log = LoggerFactory.getLogger(JobEventListener.class);

    private final OutputDeliveryService outputDeliveryService;

    public JobEventListener(OutputDeliveryService outputDeliveryService) {
        this.outputDeliveryService = outputDeliveryService;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue,  // broker-named, exclusive, auto-delete, non-durable
            exchange = @Exchange(value = QueueNames.JOB_EVENTS_EXCHANGE, type = ExchangeTypes.FANOUT, durable = "true")))
    public void onJobEvent(JobEvent event, Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            outputDeliveryService.onJobEvent(event.jobId());
        } catch (Exception e) {
            log.warn("[job-events] failed to push snapshot for job {}: {}", event.jobId(), e.toString());
        } finally {
            ack(channel, deliveryTag);
        }
    }

    private void ack(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.debug("[job-events] ack failed for delivery {}", deliveryTag, e);
        }
    }
}
