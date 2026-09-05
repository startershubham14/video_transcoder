package dev.shubham.transcoder.messaging;

import dev.shubham.transcoder.media.ProcessExecutionException;
import dev.shubham.transcoder.prepare.PrepareRejectedException;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * Classifies a stage failure as {@link FailureDisposition#TRANSIENT} or
 * {@link FailureDisposition#PERMANENT}. Retrying permanent errors is wasted work and the
 * main cause of retry-storm throughput collapse, so this single decision is where the
 * queue's stability is won. Shared by every stage worker via {@link AbstractStageWorker}.
 *
 * <p>The rules walk the cause chain (I/O failures are usually wrapped). This is the pipeline's
 * error-routing extension point: a newly-recognised failure mode is added here, not in the
 * retry dispatcher.
 */
@Component
public class ErrorClassifier {

    public FailureDisposition classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            // --- permanent: retrying cannot succeed ---
            if (t instanceof PrepareRejectedException      // invalid / over-limit / infected input
                    || t instanceof NoSuchKeyException     // referenced object is gone
                    || t instanceof ProcessExecutionException) { // ffmpeg/ffprobe rejected the media
                return FailureDisposition.PERMANENT;
            }
            // --- AWS service errors: 5xx/429 transient, other 4xx are definitive ---
            if (t instanceof AwsServiceException aws) {
                return isRetryableStatus(aws.statusCode())
                        ? FailureDisposition.TRANSIENT
                        : FailureDisposition.PERMANENT;
            }
            // --- transient: worth a backed-off retry ---
            if (t instanceof SdkClientException          // AWS client-side (network) failure
                    || t instanceof SocketTimeoutException
                    || t instanceof ConnectException
                    || t instanceof IOException
                    || t instanceof AmqpException) {
                return FailureDisposition.TRANSIENT;
            }
            if (t.getCause() == t) {
                break; // defensive: self-referential cause
            }
        }
        // Unknown failures default to TRANSIENT — the retry cap bounds the cost of a
        // misclassification, whereas dead-lettering a recoverable error loses the job.
        return FailureDisposition.TRANSIENT;
    }

    /** 5xx (server) and 429 (throttling) are worth retrying; other 4xx are permanent. */
    private static boolean isRetryableStatus(int status) {
        return status >= 500 || status == 429;
    }
}
