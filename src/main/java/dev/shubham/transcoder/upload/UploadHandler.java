package dev.shubham.transcoder.upload;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.job.Job;
import dev.shubham.transcoder.job.JobRepository;
import dev.shubham.transcoder.job.JobStatus;
import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.messaging.TaskPublisher;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.storage.PresignedMultipartUpload;
import dev.shubham.transcoder.storage.StorageProperties;
import dev.shubham.transcoder.upload.dto.CompleteUploadRequest;
import dev.shubham.transcoder.upload.dto.CreateUploadResponse;
import dev.shubham.transcoder.user.User;
import dev.shubham.transcoder.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the thin upload flow (orchestration + state). On create: persist a job in
 * {@code AWAITING_UPLOAD} (with an upload deadline) and initiate an S3 multipart upload,
 * returning presigned part URLs. On complete: call {@code CompleteMultipartUpload}
 * (authoritative), verify size, transition to {@code PREPARING}, then — after the DB commit —
 * enqueue the prepare task (enqueue-after-commit). Transport is {@link UploadController}.
 */
@Service
public class UploadHandler {

    /** S3 hard cap on parts per multipart upload. */
    private static final int MAX_PARTS = 10_000;

    /** v1 has no auth (see CLAUDE.md scope guards); all jobs belong to one default user. */
    // TODO replace with the authenticated user once auth exists.
    private static final String DEFAULT_USER_EMAIL = "default@local";

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final BlobStore blobStore;
    private final TaskPublisher taskPublisher;
    private final PipelineProperties pipelineProperties;
    private final StorageProperties storageProperties;

    public UploadHandler(JobRepository jobRepository,
                         UserRepository userRepository,
                         BlobStore blobStore,
                         TaskPublisher taskPublisher,
                         PipelineProperties pipelineProperties,
                         StorageProperties storageProperties) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.blobStore = blobStore;
        this.taskPublisher = taskPublisher;
        this.pipelineProperties = pipelineProperties;
        this.storageProperties = storageProperties;
    }

    /** Create the job + multipart upload; return presigned part URLs. */
    @Transactional
    public CreateUploadResponse createUpload(String filename, long sizeBytes, String contentType) {
        // Cheap, untrusted early gate; ffprobe re-checks authoritatively in prepare.
        if (sizeBytes <= 0 || sizeBytes > pipelineProperties.maxSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "declared size exceeds MAX_SIZE_BYTES");
        }

        User user = defaultUser();
        Duration ttl = Duration.ofMinutes(pipelineProperties.uploadDeadlineMinutes());
        Job job = jobRepository.save(Job.create(user.getId(), Instant.now().plus(ttl)));

        String sourceKey = job.getId() + "/source.mp4";
        int parts = partCount(sizeBytes, storageProperties.s3().partSizeBytes());
        PresignedMultipartUpload upload = blobStore.initiateMultipartUpload(sourceKey, parts, ttl);
        job.assignSourceKey(sourceKey); // dirty-checked, flushed on commit

        List<String> partUrls = upload.partUrls().stream().map(URL::toString).toList();
        return new CreateUploadResponse(job.getId(), upload.uploadId(), partUrls, ttl.toSeconds());
    }

    /** Finalize the upload and enqueue prepare (commit state first, then publish). */
    @Transactional
    public void completeUpload(UUID jobId, CompleteUploadRequest request) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "job not found"));
        if (job.getStatus() != JobStatus.AWAITING_UPLOAD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "job is not awaiting upload");
        }

        // Authoritative: CompleteMultipartUpload succeeds only if every part is present/intact.
        blobStore.completeMultipartUpload(job.getSourceKey(), request.uploadId(), request.partETags());
        if (blobStore.objectSize(job.getSourceKey()) > pipelineProperties.maxSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "uploaded size exceeds MAX_SIZE_BYTES");
        }

        job.markPreparing(); // AWAITING_UPLOAD -> PREPARING (guarded)

        // Enqueue-after-commit: publish only once the state change is durably committed.
        String sourceKey = job.getSourceKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskPublisher.publishPrepare(new PrepareTask(jobId, sourceKey));
            }
        });
    }

    private User defaultUser() {
        return userRepository.findByEmail(DEFAULT_USER_EMAIL)
                .orElseGet(() -> userRepository.save(User.create(DEFAULT_USER_EMAIL)));
    }

    /** Parts needed to cover {@code sizeBytes} at {@code partSizeBytes} each, clamped to S3 limits. */
    static int partCount(long sizeBytes, long partSizeBytes) {
        long count = (sizeBytes + partSizeBytes - 1) / partSizeBytes; // ceil
        return (int) Math.max(1, Math.min(count, MAX_PARTS));
    }
}
