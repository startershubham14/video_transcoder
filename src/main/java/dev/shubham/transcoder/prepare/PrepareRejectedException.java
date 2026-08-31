package dev.shubham.transcoder.prepare;

/**
 * Signals a <em>permanent</em>, input-caused prepare failure (invalid/corrupt media, over a
 * limit, or infected). The handler catches this and fails the job with the reason — it is not
 * retried. Infrastructure errors (S3 down, ffmpeg crash) are not this type and propagate to be
 * dead-lettered.
 */
public class PrepareRejectedException extends RuntimeException {

    public PrepareRejectedException(String reason) {
        super(reason);
    }
}
