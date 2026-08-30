package dev.shubham.transcoder.job;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Server-Sent Events stream for live progress and output-URL delivery. If the client is
 * connected, completion pushes download URLs immediately; if it has gone, outputs are
 * already persisted and the client picks them up via {@link JobController} on next poll.
 */
@RestController
@RequestMapping("/jobs")
public class StatusStreamController {

    @GetMapping("/{id}/events")
    public SseEmitter stream(@PathVariable("id") UUID jobId) {
        // TODO register the emitter with OutputDeliveryService keyed by jobId.
        throw new UnsupportedOperationException("not implemented");
    }
}
