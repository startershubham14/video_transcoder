package dev.shubham.transcoder.messaging;

import com.rabbitmq.client.Channel;
import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.config.RabbitMqConfig;
import dev.shubham.transcoder.prepare.PrepareRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test of the messaging reliability wiring against a <em>real</em> broker (Testcontainers
 * RabbitMQ) — the doc's must-test that must not be mocked. Loads the real {@link RabbitMqConfig}
 * topology (stage queue → DLX; retry.exchange → retry.delay.queue → back to origin) and a failing
 * listener, then asserts:
 * <ul>
 *   <li>a <b>permanent</b> failure dead-letters on the first attempt (no retry);</li>
 *   <li>a <b>transient</b> failure cycles through the retry-delay queue up to the attempt cap, then
 *       dead-letters — proving the retry-exchange returns the message to its origin stage queue.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
class ErrorRoutingIntegrationTest {

    @Container
    static RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void rabbit(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.acknowledge-mode", () -> "manual");
    }

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    RabbitAdmin rabbitAdmin;
    @Autowired
    FailingListener listener;

    @BeforeEach
    void reset() {
        rabbitAdmin.purgeQueue(QueueNames.TRANSCODE_QUEUE, false);
        rabbitAdmin.purgeQueue(QueueNames.RETRY_DELAY_QUEUE, false);
        rabbitAdmin.purgeQueue(QueueNames.DEAD_LETTER_QUEUE, false);
        listener.reset();
    }

    @Test
    void permanentFailureDeadLettersOnFirstAttempt() {
        listener.failWith(new PrepareRejectedException("corrupt input"));

        rabbitTemplate.convertAndSend(QueueNames.TRANSCODE_QUEUE, "task-permanent");

        Message dead = awaitDeadLetter();
        assertNotNull(dead, "permanent failure must dead-letter");
        assertTrue(listener.processCount() >= 1, "processed at least once");
        // no retries: processed exactly once
        assertTrue(listener.processCount() == 1, "permanent failure must not be retried");
    }

    @Test
    void transientFailureRetriesThenDeadLetters() {
        // TRANSIENT (IOException) with cap=2 → attempts 0,1,2 = 3 process calls, then DLQ.
        listener.failWith(new RuntimeException(new IOException("s3 blip")));

        rabbitTemplate.convertAndSend(QueueNames.TRANSCODE_QUEUE, "task-transient");

        Message dead = awaitDeadLetter();
        assertNotNull(dead, "exhausted transient retries must dead-letter");
        assertTrue(listener.processCount() >= 3,
                "expected initial + 2 retries before DLQ, got " + listener.processCount());
    }

    private Message awaitDeadLetter() {
        await().atMost(ofSeconds(20)).until(() -> rabbitAdmin.getQueueInfo(QueueNames.DEAD_LETTER_QUEUE)
                .getMessageCount() > 0);
        return rabbitTemplate.receive(QueueNames.DEAD_LETTER_QUEUE, 2000);
    }

    // --- context: real topology + rabbit autoconfig + a failing listener on the transcode queue ---

    @SpringBootConfiguration
    @EnableRabbit
    @ImportAutoConfiguration(RabbitAutoConfiguration.class)
    @Import(RabbitMqConfig.class)
    static class TestConfig {

        @Bean
        ErrorClassifier errorClassifier() {
            return new ErrorClassifier();
        }

        @Bean
        RetryPublisher retryPublisher(RabbitTemplate rabbitTemplate) {
            return new RetryPublisher(rabbitTemplate);
        }

        @Bean
        PipelineProperties pipelineProperties() {
            // cap = 2 retries, 1s backoff each — fast, deterministic cycles through the delay queue.
            return new PipelineProperties("mp4", 8, 300, 2_147_483_648L, 2, List.of(1), 50, 60, 60, 120);
        }

        @Bean
        FailingListener failingListener(ErrorClassifier c, RetryPublisher rp, PipelineProperties p) {
            return new FailingListener(c, rp, p);
        }
    }

    /** A transcode-queue listener whose body throws a configured exception; counts invocations. */
    static class FailingListener extends AbstractStageWorker<String> {
        private final AtomicInteger processCount = new AtomicInteger();
        private volatile RuntimeException toThrow = new RuntimeException("unset");

        FailingListener(ErrorClassifier c, RetryPublisher rp, PipelineProperties p) {
            super(c, rp, p, QueueNames.TRANSCODE_QUEUE);
        }

        void failWith(RuntimeException e) {
            this.toThrow = e;
        }

        void reset() {
            processCount.set(0);
        }

        int processCount() {
            return processCount.get();
        }

        @RabbitListener(queues = QueueNames.TRANSCODE_QUEUE)
        public void onMessage(String task, Message message, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
            execute(task, message, channel, deliveryTag);
        }

        @Override
        protected void process(String task) {
            processCount.incrementAndGet();
            throw toThrow;
        }

        @Override
        protected String stageName() {
            return "test-transcode";
        }
    }
}
