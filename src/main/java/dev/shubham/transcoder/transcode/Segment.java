package dev.shubham.transcoder.transcode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One (rung × keyframe segment) unit of transcode work. Maps the {@code segments} table. The
 * {@code (job_id, rung, segment_index)} uniqueness makes fan-out idempotent. Created via
 * {@link #create}; state changes go through the intent methods, never a setter.
 */
@Entity
@Table(name = "segments", uniqueConstraints =
        @UniqueConstraint(columnNames = {"job_id", "rung", "segment_index"}))
public class Segment {

    /** Legal segment transitions (see the state machine in §4). */
    private static final Map<SegmentStatus, Set<SegmentStatus>> LEGAL = new EnumMap<>(SegmentStatus.class);
    static {
        LEGAL.put(SegmentStatus.QUEUED, EnumSet.of(SegmentStatus.PROCESSING));
        LEGAL.put(SegmentStatus.PROCESSING, EnumSet.of(
                SegmentStatus.DONE, SegmentStatus.QUEUED, SegmentStatus.RETRY_WAIT, SegmentStatus.FAILED));
        LEGAL.put(SegmentStatus.RETRY_WAIT, EnumSet.of(SegmentStatus.QUEUED));
        LEGAL.put(SegmentStatus.DONE, EnumSet.noneOf(SegmentStatus.class));
        LEGAL.put(SegmentStatus.FAILED, EnumSet.noneOf(SegmentStatus.class));
    }

    // DB generates the id via the `gen_random_uuid()` default in V1__init.sql; Hibernate
    // excludes it from INSERT and reads the generated value back.
    @Id
    @Generated(event = EventType.INSERT)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String rung;

    /** Ordering for concat / playlist assembly. */
    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    // Persisted as the enum name in a varchar column (see V2). Plain STRING mapping avoids
    // the per-type SQL cast that native Postgres enums force in bulk JPQL updates.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SegmentStatus status;

    /** Redelivery counter, checked against {@code pipeline.retry-max-attempts}. */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "source_segment_key", nullable = false)
    private String sourceSegmentKey;

    @Column(name = "output_segment_key")
    private String outputSegmentKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Segment() {
        // for JPA
    }

    /** Create a QUEUED segment for a rung. The id is generated on persist. */
    public static Segment create(UUID jobId, Rung rung, int segmentIndex, String sourceSegmentKey) {
        Segment segment = new Segment();
        segment.jobId = jobId;
        segment.rung = rung.label();
        segment.segmentIndex = segmentIndex;
        segment.sourceSegmentKey = sourceSegmentKey;
        segment.status = SegmentStatus.QUEUED;
        segment.attempts = 0;
        return segment;
    }

    // --- state transitions (intent verbs, guarded) ---

    public void transitionTo(SegmentStatus target) {
        if (!LEGAL.getOrDefault(status, Set.of()).contains(target)) {
            throw new IllegalStateException("Illegal segment transition " + status + " -> " + target);
        }
        this.status = target;
    }

    public void markProcessing() {
        transitionTo(SegmentStatus.PROCESSING);
    }

    public void markDone(String outputSegmentKey) {
        this.outputSegmentKey = outputSegmentKey;
        transitionTo(SegmentStatus.DONE);
    }

    public void markRetryWait() {
        transitionTo(SegmentStatus.RETRY_WAIT);
    }

    public void requeue() {
        transitionTo(SegmentStatus.QUEUED);
    }

    public void markFailed() {
        transitionTo(SegmentStatus.FAILED);
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    // --- getters ---

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getRung() {
        return rung;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public SegmentStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getSourceSegmentKey() {
        return sourceSegmentKey;
    }

    public String getOutputSegmentKey() {
        return outputSegmentKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
