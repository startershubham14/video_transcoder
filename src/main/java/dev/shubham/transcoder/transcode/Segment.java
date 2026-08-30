package dev.shubham.transcoder.transcode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.UUID;

/**
 * One (rung × keyframe segment) unit of transcode work. Maps the {@code segments} table.
 * The {@code (job_id, rung, segment_index)} uniqueness makes fan-out idempotent — a
 * replayed prepare cannot create duplicate rows.
 */
@Entity
@Table(name = "segments", uniqueConstraints =
        @UniqueConstraint(columnNames = {"job_id", "rung", "segment_index"}))
public class Segment {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String rung;

    /** Ordering for concat / playlist assembly. */
    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    // Maps the Java enum to the Postgres `segment_status` enum type from V1__init.sql.
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "segment_status")
    private SegmentStatus status;

    /** Redelivery counter, checked against {@code pipeline.retry-max-attempts}. */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "source_segment_key", nullable = false)
    private String sourceSegmentKey;

    @Column(name = "output_segment_key")
    private String outputSegmentKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Segment() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getRung() {
        return rung;
    }

    public void setRung(String rung) {
        this.rung = rung;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(int segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public SegmentStatus getStatus() {
        return status;
    }

    public void setStatus(SegmentStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getSourceSegmentKey() {
        return sourceSegmentKey;
    }

    public void setSourceSegmentKey(String sourceSegmentKey) {
        this.sourceSegmentKey = sourceSegmentKey;
    }

    public String getOutputSegmentKey() {
        return outputSegmentKey;
    }

    public void setOutputSegmentKey(String outputSegmentKey) {
        this.outputSegmentKey = outputSegmentKey;
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
