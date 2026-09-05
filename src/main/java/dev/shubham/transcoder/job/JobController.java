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

    private final JobStatusService jobStatusService;

    public JobController(JobStatusService jobStatusService) {
        this.jobStatusService = jobStatusService;
    }

    @GetMapping("/{id}")
    public JobStatusResponse getJobStatus(@PathVariable("id") UUID jobId) {
        return jobStatusService.getStatus(jobId); // 404 raised by the service for an unknown job
    }
}
