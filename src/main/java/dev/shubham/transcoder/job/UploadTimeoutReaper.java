package dev.shubham.transcoder.job;

import dev.shubham.transcoder.messaging.JobEventPublisher;
import dev.shubham.transcoder.storage.BlobStore;
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
 * Scheduled backstop for uploads that never complete. A job stuck in {@code AWAITING_UPLOAD} past its
 * deadline is marked {@code EXPIRED} (so the client sees the failure on next poll) and its dangling S3
 * multipart upload is aborted — freeing the accumulated parts. S3 emits no event for a failed/abandoned
 * upload, so detection is server-driven; the abort uses the persisted {@code uploadId}. Runs in the
 * {@code api} profile.
 */
@Component
@Profile("api")
public class UploadTimeoutReaper {

    private static final Logger log = LoggerFactory.getLogger(UploadTimeoutReaper.class);

    private final JobRepository jobRepository;
    private final BlobStore blobStore;
    private final JobEventPublisher jobEventPublisher;
    private final TransactionTemplate transactionTemplate;

    public UploadTimeoutReaper(JobRepository jobRepository,
                               BlobStore blobStore,
                               JobEventPublisher jobEventPublisher,
                               PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.blobStore = blobStore;
        this.jobEventPublisher = jobEventPublisher;
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
            // Capture key+uploadId under the transaction; abort S3 afterwards (best-effort, non-txn).
            boolean[] expired = {false};
            String[] toAbort = new String[2]; // [sourceKey, uploadId]
            transactionTemplate.executeWithoutResult(status -> {
                Job job = jobRepository.findById(candidate.getId()).orElse(null);
                if (job != null && job.getStatus() == JobStatus.AWAITING_UPLOAD) {
                    job.markExpired(); // AWAITING_UPLOAD -> EXPIRED
                    expired[0] = true;
                    toAbort[0] = job.getSourceKey();
                    toAbort[1] = job.getUploadId();
                    log.info("[reaper] job {} EXPIRED (upload deadline passed)", job.getId());
                }
            });
            if (!expired[0]) {
                continue; // already progressed since the query — nothing to do
            }
            if (toAbort[0] != null && toAbort[1] != null) {
                try {
                    blobStore.abortMultipartUpload(toAbort[0], toAbort[1]);
                } catch (RuntimeException e) {
                    // Already completed/aborted, or transient S3 issue — the S3 lifecycle rule is the
                    // backstop. Don't fail the sweep over a best-effort cleanup.
                    log.warn("[reaper] abort multipart failed for job {} (best-effort): {}",
                            candidate.getId(), e.toString());
                }
            }
            jobEventPublisher.publish(candidate.getId()); // notify SSE watchers: job → EXPIRED
        }
    }
}
