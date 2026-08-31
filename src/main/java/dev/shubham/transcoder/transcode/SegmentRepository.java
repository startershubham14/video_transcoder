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
     * Mark my segment DONE, idempotently.
     *
     * @return rows affected (0 if it was already DONE)
     */
    @Modifying
    @Query("""
            update Segment s
               set s.status = dev.shubham.transcoder.transcode.SegmentStatus.DONE,
                   s.updatedAt = CURRENT_TIMESTAMP
             where s.id = :segmentId
               and s.status <> dev.shubham.transcoder.transcode.SegmentStatus.DONE
            """)
    int markDone(@Param("segmentId") UUID segmentId);

    /**
     * Claim the concat/package trigger for one rung: succeeds for exactly one worker,
     * only when no segment in that rung is still un-DONE. TODO: this is expressed against
     * the {@code jobs} table and is easier to keep as native SQL — implement as the
     * guarded {@code UPDATE jobs ... WHERE NOT EXISTS (unfinished segment in this rung)}
     * from the design doc.
     *
     * @return 1 if this worker won the claim, 0 otherwise
     */
    // TODO @Modifying @Query(nativeQuery = true, value = "...") int claimRungCompletion(jobId, rung);
}
