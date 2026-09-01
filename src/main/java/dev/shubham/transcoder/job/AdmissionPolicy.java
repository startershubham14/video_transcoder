package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.PipelineProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Admission-control decision: whether a new job may be accepted, i.e. the count of in-flight
 * jobs is below {@code pipeline.in-flight-job-cap}. Used by {@link AdmissionControlInterceptor}
 * to reject {@code POST /uploads} with {@code 429} when over the cap. Complements nginx's
 * coarse per-IP limits — the proxy knows requests/IPs, this policy knows jobs.
 */
@Component
public class AdmissionPolicy {

    /** Jobs actively holding pipeline resources (not yet terminal, past upload). */
    private static final List<JobStatus> IN_FLIGHT =
            List.of(JobStatus.PREPARING, JobStatus.PROCESSING, JobStatus.CONCATENATING);

    private final JobRepository jobRepository;
    private final PipelineProperties pipelineProperties;

    public AdmissionPolicy(JobRepository jobRepository, PipelineProperties pipelineProperties) {
        this.jobRepository = jobRepository;
        this.pipelineProperties = pipelineProperties;
    }

    /** @return true if a new job may be admitted (in-flight count below the configured cap). */
    public boolean canAdmit() {
        return jobRepository.countByStatusIn(IN_FLIGHT) < pipelineProperties.inFlightJobCap();
    }
}
