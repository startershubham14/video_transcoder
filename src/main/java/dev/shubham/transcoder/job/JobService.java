package dev.shubham.transcoder.job;

import dev.shubham.transcoder.job.dto.JobStatusResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Read-side job operations: assemble the status/progress view and decide admission.
 * State transitions themselves are owned by the stage services and the atomic fan-in
 * update.
 */
@Service
public class JobService {

    /** Build the poll response: status, derived progress, and (if done) output URLs. */
    public JobStatusResponse getStatus(UUID jobId) {
        // TODO load job + segment aggregate; mint presigned GETs when COMPLETED.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Whether a new job may be admitted (in-flight count below the configured cap). */
    public boolean canAdmit() {
        // TODO compare JobRepository.countByStatusIn(...) against the cap.
        throw new UnsupportedOperationException("not implemented");
    }
}
