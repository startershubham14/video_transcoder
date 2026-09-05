package dev.shubham.transcoder.messaging;

import dev.shubham.transcoder.media.ProcessExecutionException;
import dev.shubham.transcoder.prepare.PrepareRejectedException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import static dev.shubham.transcoder.messaging.FailureDisposition.PERMANENT;
import static dev.shubham.transcoder.messaging.FailureDisposition.TRANSIENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the transient/permanent routing decision (the queue-stability lever, doc §5).
 */
class ErrorClassifierTest {

    private final ErrorClassifier classifier = new ErrorClassifier();

    @Test
    void permanentInputErrors() {
        assertEquals(PERMANENT, classifier.classify(new PrepareRejectedException("infected")));
        assertEquals(PERMANENT, classifier.classify(NoSuchKeyException.builder().message("gone").build()));
        assertEquals(PERMANENT, classifier.classify(
                new ProcessExecutionException(List.of("ffmpeg"), 1, "bad media")));
    }

    @Test
    void transientInfraErrors() {
        assertEquals(TRANSIENT, classifier.classify(
                (Throwable) S3Exception.builder().statusCode(503).message("slow down").build()));
        assertEquals(TRANSIENT, classifier.classify(
                (Throwable) S3Exception.builder().statusCode(429).message("throttled").build()));
        assertEquals(TRANSIENT, classifier.classify(SdkClientException.builder().message("reset").build()));
        assertEquals(TRANSIENT, classifier.classify(new SocketTimeoutException("timeout")));
        assertEquals(TRANSIENT, classifier.classify(new IOException("io")));
    }

    @Test
    void definitiveAws4xxIsPermanent() {
        assertEquals(PERMANENT, classifier.classify(
                (Throwable) S3Exception.builder().statusCode(403).message("forbidden").build()));
    }

    @Test
    void walksCauseChain() {
        Throwable wrapped = new RuntimeException("wrapper", new IOException("network blip"));
        assertEquals(TRANSIENT, classifier.classify(wrapped));

        Throwable wrappedPermanent = new IllegalStateException("x", new PrepareRejectedException("over limit"));
        assertEquals(PERMANENT, classifier.classify(wrappedPermanent));
    }

    @Test
    void unknownDefaultsToTransient() {
        assertEquals(TRANSIENT, classifier.classify(new RuntimeException("mystery")));
    }
}
