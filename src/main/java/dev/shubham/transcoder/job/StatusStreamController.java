package dev.shubham.transcoder.job;

import dev.shubham.transcoder.config.SseProperties;
import dev.shubham.transcoder.job.dto.JobStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.UUID;

/**
 * Server-Sent Events stream for live progress and output-URL delivery. Each connection gets an
 * immediate snapshot, then a {@code status} event on every {@code JobEvent} poke until the job
 * reaches a terminal state, at which point the stream closes. Polling ({@link JobController}) is
 * the baseline; this is the push layer on top.
 */
@RestController
@RequestMapping("/jobs")
public class StatusStreamController {

    private final JobStatusService jobStatusService;
    private final OutputDeliveryService outputDeliveryService;
    private final long timeoutMillis;

    public StatusStreamController(JobStatusService jobStatusService,
                                  OutputDeliveryService outputDeliveryService,
                                  SseProperties sseProperties) {
        this.jobStatusService = jobStatusService;
        this.outputDeliveryService = outputDeliveryService;
        this.timeoutMillis = Duration.ofMinutes(sseProperties.timeoutMinutes()).toMillis();
    }

    @GetMapping("/{id}/events")
    public SseEmitter stream(@PathVariable("id") UUID jobId) {
        // Validate + snapshot up front: an unknown job surfaces as a clean 404 (via the advice)
        // instead of an opened-then-errored stream.
        JobStatusResponse snapshot = jobStatusService.getStatus(jobId);
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        outputDeliveryService.register(jobId, emitter, snapshot);
        return emitter;
    }
}
