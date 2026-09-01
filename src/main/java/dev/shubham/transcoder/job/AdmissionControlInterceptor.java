package dev.shubham.transcoder.job;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * App-level (semantic) admission control: caps the number of in-flight jobs and rejects
 * new upload requests with {@code 429} when over the cap. Complements nginx's coarse
 * per-IP limits — the proxy knows requests/IPs, the app knows jobs.
 */
@Component
public class AdmissionControlInterceptor implements HandlerInterceptor {

    private final AdmissionPolicy admissionPolicy;

    public AdmissionControlInterceptor(AdmissionPolicy admissionPolicy) {
        this.admissionPolicy = admissionPolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (admissionPolicy.canAdmit()) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "30");
        return false; // over the in-flight cap
    }
}
