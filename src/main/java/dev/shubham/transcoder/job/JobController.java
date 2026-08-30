package dev.shubham.transcoder.job;

import dev.shubham.transcoder.job.dto.JobStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Status endpoint. Polling is the baseline delivery mechanism; SSE push
 * ({@link StatusStreamController}) is layered on top.
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{id}")
    public JobStatusResponse get(@PathVariable("id") UUID jobId) {
        // TODO map not-found to 404.
        return jobService.getStatus(jobId);
    }
}
