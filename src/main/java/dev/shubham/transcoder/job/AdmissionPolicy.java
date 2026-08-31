package dev.shubham.transcoder.job;

import org.springframework.stereotype.Component;

/**
 * Admission-control decision: whether a new job may be accepted, i.e. the count of in-flight
 * jobs is below {@code pipeline.in-flight-job-cap}. Used by {@link AdmissionControlInterceptor}
 * to reject {@code POST /uploads} with {@code 429} when over the cap. Complements nginx's
 * coarse per-IP limits — the proxy knows requests/IPs, this policy knows jobs.
 */
@Component
public class AdmissionPolicy {

    /** @return true if a new job may be admitted. */
    public boolean canAdmit() {
        // TODO compare JobRepository.countByStatusIn(...) against the configured cap.
        throw new UnsupportedOperationException("not implemented");
    }
}
