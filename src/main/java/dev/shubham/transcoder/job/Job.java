package dev.shubham.transcoder.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A transcoding job — the unit of work and the source of truth for its own state.
 * Maps the {@code jobs} table (see the schema in
 * {@code docs/architecture-and-workflow.md} §9). Probed metadata is filled in by the
 * prepare stage after ffprobe.
 */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public Integer getSourceWidth() {
        return sourceWidth;
    }

    public void setSourceWidth(Integer sourceWidth) {
        this.sourceWidth = sourceWidth;
    }

    public Integer getSourceHeight() {
        return sourceHeight;
    }

    public void setSourceHeight(Integer sourceHeight) {
        this.sourceHeight = sourceHeight;
    }

    public BigDecimal getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(BigDecimal durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public BigDecimal getFps() {
        return fps;
    }

    public void setFps(BigDecimal fps) {
        this.fps = fps;
    }

    public String getSourceCodec() {
        return sourceCodec;
    }

    public void setSourceCodec(String sourceCodec) {
        this.sourceCodec = sourceCodec;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getManifestKey() {
        return manifestKey;
    }

    public void setManifestKey(String manifestKey) {
        this.manifestKey = manifestKey;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public Instant getUploadDeadline() {
        return uploadDeadline;
    }

    public void setUploadDeadline(Instant uploadDeadline) {
        this.uploadDeadline = uploadDeadline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
