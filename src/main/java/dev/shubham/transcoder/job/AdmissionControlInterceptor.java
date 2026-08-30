package dev.shubham.transcoder.job;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * App-level (semantic) admission control: caps the number of in-flight jobs and rejects
 * new upload requests with {@code 429} when over the cap. Complements nginx's coarse
 * per-IP limits — the proxy knows requests/IPs, the app knows jobs.
 */
@Component
public class AdmissionControlInterceptor implements HandlerInterceptor {

    private final JobService jobService;

    public AdmissionControlInterceptor(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // TODO if (!jobService.canAdmit()) { set 429 + Retry-After; return false; }
        throw new UnsupportedOperationException("not implemented");
    }
}
