package dev.shubham.transcoder.messaging;

import com.rabbitmq.client.Channel;
import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.prepare.PrepareRejectedException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePropertiesBuilder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies the transient-vs-permanent routing in {@link AbstractStageWorker} (doc §5): permanent
 * errors dead-letter on the first attempt; transient errors retry with backoff until the cap, then
 * dead-letter. Uses a tiny worker subclass with mocked transport — no broker needed.
 */
class ErrorRoutingTest {

    private static final long TAG = 7L;

    private static PipelineProperties props() {
        return new PipelineProperties("mp4", 8, 300, 2_147_483_648L, 3, List.of(2, 8, 30), 50, 60, 60, 120);
    }

    /** Worker whose body throws a supplied error; records whether onGiveUp fired. */
    private static final class TestWorker extends AbstractStageWorker<String> {
        private final RuntimeException toThrow;
        boolean gaveUp = false;

        TestWorker(RetryPublisher retryPublisher, RuntimeException toThrow) {
            super(new ErrorClassifier(), retryPublisher, props(), "test.queue");
            this.toThrow = toThrow;
        }

        void run(Message message, Channel channel) {
            execute("task", message, channel, TAG);
        }

        @Override
        protected void process(String task) {
            if (toThrow != null) {
                throw toThrow;
            }
        }

        @Override
        protected String stageName() {
            return "test";
        }

        @Override
        protected void onGiveUp(String task, Throwable cause) {
            gaveUp = true;
        }
    }

    private static Message messageWithAttempts(Integer attempts) {
        var builder = MessagePropertiesBuilder.newInstance();
        if (attempts != null) {
            builder.setHeader(RetryPublisher.RETRY_ATTEMPTS_HEADER, attempts);
        }
        return new Message(new byte[0], builder.build());
    }

    @Test
    void permanentErrorsDeadLetterOnFirstAttempt() throws IOException {
        RetryPublisher retryPublisher = mock(RetryPublisher.class);
        Channel channel = mock(Channel.class);
        TestWorker worker = new TestWorker(retryPublisher, new PrepareRejectedException("corrupt"));

        worker.run(messageWithAttempts(null), channel);

        verify(retryPublisher, never()).scheduleRetry(any(), anyString(), anyInt(), anyLong());
        verify(channel).basicNack(TAG, false, false); // → DLX → dead-letter.queue
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        assertTrue(worker.gaveUp);
    }

    @Test
    void transientErrorRetriesWithBackoffThenAcks() throws IOException {
        RetryPublisher retryPublisher = mock(RetryPublisher.class);
        Channel channel = mock(Channel.class);
        TestWorker worker = new TestWorker(retryPublisher, new RuntimeException(new IOException("blip")));

        worker.run(messageWithAttempts(0), channel); // first failure

        verify(retryPublisher).scheduleRetry(any(Message.class), eq("test.queue"), eq(1), eq(2000L));
        verify(channel).basicAck(TAG, false); // original acked; the delayed copy carries the work
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertFalse(worker.gaveUp);
    }

    @Test
    void transientErrorUsesEscalatingBackoff() throws IOException {
        RetryPublisher retryPublisher = mock(RetryPublisher.class);
        Channel channel = mock(Channel.class);
        TestWorker worker = new TestWorker(retryPublisher, new RuntimeException(new IOException("blip")));

        worker.run(messageWithAttempts(1), channel); // second attempt → 8s backoff

        verify(retryPublisher).scheduleRetry(any(Message.class), eq("test.queue"), eq(2), eq(8000L));
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void transientErrorsDeadLetterOnceRetriesExhausted() throws IOException {
        RetryPublisher retryPublisher = mock(RetryPublisher.class);
        Channel channel = mock(Channel.class);
        TestWorker worker = new TestWorker(retryPublisher, new RuntimeException(new IOException("blip")));

        worker.run(messageWithAttempts(3), channel); // already at RETRY_MAX_ATTEMPTS

        verify(retryPublisher, never()).scheduleRetry(any(), anyString(), anyInt(), anyLong());
        verify(channel).basicNack(TAG, false, false);
        assertTrue(worker.gaveUp);
    }

    @Test
    void successAcksWithoutRetry() throws IOException {
        RetryPublisher retryPublisher = mock(RetryPublisher.class);
        Channel channel = mock(Channel.class);
        TestWorker worker = new TestWorker(retryPublisher, null);

        worker.run(messageWithAttempts(null), channel);

        verify(channel).basicAck(TAG, false);
        verify(retryPublisher, never()).scheduleRetry(any(), anyString(), anyInt(), anyLong());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertFalse(worker.gaveUp);
    }
}
