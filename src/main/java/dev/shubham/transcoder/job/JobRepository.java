package dev.shubham.transcoder.job;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
