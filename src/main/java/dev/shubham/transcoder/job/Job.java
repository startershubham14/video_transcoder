package dev.shubham.transcoder.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A transcoding job — the unit of work and the source of truth for its own state. Maps the
 * {@code jobs} table (schema in {@code docs/architecture-and-workflow.md} §9). Created via
 * {@link #create}; state changes go through {@link #transitionTo} (guarded) and the intent
 * methods below, never a setter. Probed metadata is filled in by the prepare stage.
 */
@Entity
@Table(name = "jobs")
public class Job {

    /** Legal job transitions (see the state machine in §3). Terminal states have no exits. */
    private static final Map<JobStatus, Set<JobStatus>> LEGAL = new EnumMap<>(JobStatus.class);
    static {
        LEGAL.put(JobStatus.AWAITING_UPLOAD, EnumSet.of(JobStatus.PREPARING, JobStatus.EXPIRED));
        LEGAL.put(JobStatus.PREPARING, EnumSet.of(JobStatus.PROCESSING, JobStatus.FAILED));
        LEGAL.put(JobStatus.PROCESSING, EnumSet.of(JobStatus.CONCATENATING, JobStatus.FAILED));
        LEGAL.put(JobStatus.CONCATENATING, EnumSet.of(JobStatus.COMPLETED, JobStatus.FAILED));
        LEGAL.put(JobStatus.COMPLETED, EnumSet.noneOf(JobStatus.class));
        LEGAL.put(JobStatus.FAILED, EnumSet.noneOf(JobStatus.class));
        LEGAL.put(JobStatus.EXPIRED, EnumSet.noneOf(JobStatus.class));
    }

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Maps the Java enum to the Postgres `job_status` enum type from V1__init.sql.
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "job_status")
    private JobStatus status;

    /** S3 key of the uploaded source, e.g. {@code {id}/source.mp4}. */
    @Column(name = "source_key")
    private String sourceKey;

    // --- probed metadata (populated in prepare) ---
    @Column(name = "source_width")
    private Integer sourceWidth;

    @Column(name = "source_height")
    private Integer sourceHeight;

    @Column(name = "duration_seconds")
    private BigDecimal durationSeconds;

    @Column(name = "fps")
    private BigDecimal fps;

    @Column(name = "source_codec")
    private String sourceCodec;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    // --- output ---
    /** Master {@code .m3u8} key for HLS; {@code null} for MP4 output. */
    @Column(name = "manifest_key")
    private String manifestKey;

    @Column(name = "error_reason")
    private String errorReason;

    /** Deadline for completing the upload; past it the timeout reaper marks EXPIRED. */
    @Column(name = "upload_deadline")
    private Instant uploadDeadline;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
        // for JPA
    }

    /** Create a new job awaiting its upload. The id is generated on persist. */
    public static Job create(UUID userId, Instant uploadDeadline) {
        Job job = new Job();
        job.userId = userId;
        job.status = JobStatus.AWAITING_UPLOAD;
        job.uploadDeadline = uploadDeadline;
        return job;
    }

    // --- state transitions (intent verbs, guarded) ---

    /** Guarded transition; rejects illegal moves per the job state machine. */
    public void transitionTo(JobStatus target) {
        if (!LEGAL.getOrDefault(status, Set.of()).contains(target)) {
            throw new IllegalStateException("Illegal job transition " + status + " -> " + target);
        }
        this.status = target;
    }

    public void markPreparing() {
        transitionTo(JobStatus.PREPARING);
    }

    public void markProcessing() {
        transitionTo(JobStatus.PROCESSING);
    }

    public void markConcatenating() {
        transitionTo(JobStatus.CONCATENATING);
    }

    public void markCompleted() {
        transitionTo(JobStatus.COMPLETED);
    }

    public void markExpired() {
        transitionTo(JobStatus.EXPIRED);
    }

    public void failWith(String reason) {
        this.errorReason = reason;
        transitionTo(JobStatus.FAILED);
    }

    public boolean isTerminal() {
        return status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.EXPIRED;
    }

    // --- field mutations (intent, not setters) ---

    public void assignSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    /** Record the authoritative metadata from ffprobe. */
    public void recordProbe(int width, int height, BigDecimal durationSeconds,
                            BigDecimal fps, String codec, long sizeBytes) {
        this.sourceWidth = width;
        this.sourceHeight = height;
        this.durationSeconds = durationSeconds;
        this.fps = fps;
        this.sourceCodec = codec;
        this.sizeBytes = sizeBytes;
    }

    public void attachManifest(String manifestKey) {
        this.manifestKey = manifestKey;
    }

    // --- getters ---

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public Integer getSourceWidth() {
        return sourceWidth;
    }

    public Integer getSourceHeight() {
        return sourceHeight;
    }

    public BigDecimal getDurationSeconds() {
        return durationSeconds;
    }

    public BigDecimal getFps() {
        return fps;
    }

    public String getSourceCodec() {
        return sourceCodec;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getManifestKey() {
        return manifestKey;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public Instant getUploadDeadline() {
        return uploadDeadline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
