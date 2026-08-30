package dev.shubham.transcoder.config;

import dev.shubham.transcoder.job.AdmissionControlInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC wiring. Registers {@link AdmissionControlInterceptor} on the upload-creation
 * endpoint so the app-level in-flight cap ({@code 429}) sits in front of controllers.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdmissionControlInterceptor admissionControlInterceptor;

    public WebConfig(AdmissionControlInterceptor admissionControlInterceptor) {
        this.admissionControlInterceptor = admissionControlInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // TODO restrict to POST /uploads once the path is finalized.
        registry.addInterceptor(admissionControlInterceptor).addPathPatterns("/uploads");
    }
}
