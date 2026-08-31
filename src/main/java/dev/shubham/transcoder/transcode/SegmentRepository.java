package dev.shubham.transcoder.transcode;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link Segment}, including the two conditional updates that make the
 * per-rung fan-in atomic and race-free (see the "atomic fan-in" section of
 * {@code docs/architecture-and-workflow.md} §9). Postgres serializes the row-level write,
 * so exactly one worker per rung sees a non-zero update count and enqueues packaging.
 */
public interface SegmentRepository extends JpaRepository<Segment, UUID> {

    List<Segment> findByJobIdAndRung(UUID jobId, String rung);

    /**
     * Mark a QUEUED segment PROCESSING (observability). Idempotent — a redelivery of an
     * already-PROCESSING/DONE segment affects 0 rows.
     */
    @Modifying
    @Query("""
            update Segment s
               set s.status = dev.shubham.transcoder.transcode.SegmentStatus.PROCESSING,
                   s.updatedAt = CURRENT_TIMESTAMP
             where s.id = :segmentId
               and s.status = dev.shubham.transcoder.transcode.SegmentStatus.QUEUED
            """)
    int markProcessing(@Param("segmentId") UUID segmentId);

    /**
     * Mark my segment DONE and record its output key, idempotently.
     *
     * @return rows affected (0 if it was already DONE)
     */
    @Modifying
    @Query("""
            update Segment s
               set s.status = dev.shubham.transcoder.transcode.SegmentStatus.DONE,
                   s.outputSegmentKey = :outputKey,
                   s.updatedAt = CURRENT_TIMESTAMP
             where s.id = :segmentId
               and s.status <> dev.shubham.transcoder.transcode.SegmentStatus.DONE
            """)
    int markDone(@Param("segmentId") UUID segmentId, @Param("outputKey") String outputKey);

    /**
     * Claim the concat/package trigger for one rung — the atomic fan-in (Golden rule 5).
     * A single guarded {@code UPDATE jobs ... WHERE NOT EXISTS (unfinished segment in this
     * rung)}; Postgres serializes the row-level write so exactly one worker per rung sees a
     * non-zero count. Native SQL because it targets the {@code jobs} table.
     *
     * @return 1 if this worker won the claim, 0 otherwise
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE jobs
               SET status = 'CONCATENATING', updated_at = now()
             WHERE id = :jobId
               AND status IN ('PROCESSING','CONCATENATING')
               AND NOT EXISTS (
                   SELECT 1 FROM segments
                    WHERE job_id = :jobId AND rung = :rung AND status <> 'DONE')
            """)
    int tryClaimPackaging(@Param("jobId") UUID jobId, @Param("rung") String rung);
}
