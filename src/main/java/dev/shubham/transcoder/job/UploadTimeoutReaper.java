package dev.shubham.transcoder.job;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled backstop for uploads that never complete. A job stuck in
 * {@code AWAITING_UPLOAD} past its deadline is marked {@code EXPIRED} and its multipart
 * upload aborted, so the client sees the failure on next poll. (S3 emits no event for a
 * failed/abandoned upload, so detection must be server-driven.) Runs in the {@code api}
 * profile.
 */
@Component
@Profile("api")
public class UploadTimeoutReaper {

    @Scheduled(fixedDelayString = "${pipeline.reaper-interval-ms:60000}")
    public void reapExpiredUploads() {
        // TODO find AWAITING_UPLOAD past deadline; abort multipart; mark EXPIRED.
        // No-op until implemented (this runs on a timer).
    }
}
