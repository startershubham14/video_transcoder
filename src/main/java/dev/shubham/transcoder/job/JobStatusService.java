package dev.shubham.transcoder.job;

import dev.shubham.transcoder.job.dto.JobStatusResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Assembles the job status/progress view from job + segment rows — genuine cross-entity
 * domain logic. State transitions themselves are owned by the stage handlers and the atomic
 * fan-in update. Admission is a separate concern ({@link AdmissionPolicy}).
 */
@Service
public class JobStatusService {

    /** Build the poll response: status, derived progress, and (if done) output URLs. */
    public JobStatusResponse getStatus(UUID jobId) {
        // TODO load job + segment aggregate; mint presigned GETs when COMPLETED.
        throw new UnsupportedOperationException("not implemented");
    }
}
