package dev.shubham.transcoder.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@link Job}. The count of in-flight jobs backs admission control;
 * the deadline query feeds the upload-timeout reaper.
 */
public interface JobRepository extends JpaRepository<Job, UUID> {

    /** In-flight jobs (anything past AWAITING_UPLOAD and not terminal) for the cap. */
    long countByStatusIn(List<JobStatus> statuses);

    /** Jobs still awaiting upload whose deadline has passed — reaper candidates. */
    List<Job> findByStatusAndUploadDeadlineBefore(JobStatus status, Instant cutoff);

    /**
     * Guarded job-completion: flip {@code CONCATENATING → COMPLETED}. Fires once even under
     * concurrent last-rung packaging (the caller has verified every rung's output exists).
     *
     * @return 1 if this caller completed the job, 0 otherwise
     */
    @Modifying
    @Query("""
            update Job j
               set j.status = dev.shubham.transcoder.job.JobStatus.COMPLETED,
                   j.updatedAt = CURRENT_TIMESTAMP
             where j.id = :jobId
               and j.status = dev.shubham.transcoder.job.JobStatus.CONCATENATING
            """)
    int tryComplete(@Param("jobId") UUID jobId);
}
