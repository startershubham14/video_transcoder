package dev.shubham.transcoder.messaging;

import org.springframework.stereotype.Component;

/**
 * Classifies a stage failure as {@link FailureDisposition#TRANSIENT} or
 * {@link FailureDisposition#PERMANENT}. Retrying permanent errors is wasted work and the
 * main cause of retry-storm throughput collapse, so this single decision is where the
 * queue's stability is won. Shared by every stage worker via {@link AbstractStageWorker}.
 */
@Component
public class ErrorClassifier {

    public FailureDisposition classify(Throwable error) {
        // TODO map known permanent failures (invalid input, missing object, malware) vs
        // TODO transient ones (S3 503/timeout, connection reset); default unknown -> TRANSIENT.
        throw new UnsupportedOperationException("not implemented");
    }
}
