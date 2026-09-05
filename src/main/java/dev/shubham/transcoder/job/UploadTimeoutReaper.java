package dev.shubham.transcoder.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled backstop for uploads that never complete. A job stuck in
 * {@code AWAITING_UPLOAD} past its deadline is marked {@code EXPIRED}, so the client sees the
 * failure on next poll. (S3 emits no event for a failed/abandoned upload, so detection must be
 * server-driven.) The dangling multipart upload is <em>not</em> aborted here — the {@code uploadId}
 * isn't persisted (upload flow keeps the schema minimal); the S3 lifecycle rule aborts incomplete
 * multiparts instead. Runs in the {@code api} profile.
 */
@Component
@Profile("api")
public class UploadTimeoutReaper {

    private static final Logger log = LoggerFactory.getLogger(UploadTimeoutReaper.class);

    private final JobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;

    public UploadTimeoutReaper(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${pipeline.reaper-interval-ms:60000}")
    public void reapExpiredUploads() {
        List<Job> stale = jobRepository.findByStatusAndUploadDeadlineBefore(
                JobStatus.AWAITING_UPLOAD, Instant.now());
        if (stale.isEmpty()) {
            return;
        }
        for (Job candidate : stale) {
            transactionTemplate.executeWithoutResult(status -> {
                Job job = jobRepository.findById(candidate.getId()).orElse(null);
                if (job != null && job.getStatus() == JobStatus.AWAITING_UPLOAD) {
                    job.markExpired(); // AWAITING_UPLOAD -> EXPIRED
                    log.info("[reaper] job {} EXPIRED (upload deadline passed)", job.getId());
                }
            });
        }
    }
}
