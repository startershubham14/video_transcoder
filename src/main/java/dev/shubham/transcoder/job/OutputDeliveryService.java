package dev.shubham.transcoder.job;

import dev.shubham.transcoder.job.dto.JobStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of live SSE subscribers per job, and the push side of the status stream. A
 * {@link dev.shubham.transcoder.messaging.JobEvent} poke (delivered by {@code JobEventListener})
 * triggers {@link #onJobEvent}, which re-reads the authoritative snapshot from
 * {@link JobStatusService} and pushes it to that job's subscribers — so progress/output-URLs are
 * derived from Postgres, never from the wire. On a terminal status the stream is completed.
 */
@Service
public class OutputDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OutputDeliveryService.class);

    private final JobStatusService jobStatusService;
    private final ConcurrentHashMap<UUID, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public OutputDeliveryService(JobStatusService jobStatusService) {
        this.jobStatusService = jobStatusService;
    }

    /**
     * Register an SSE subscriber for a job and immediately push the current snapshot (so a late or
     * already-finished client gets state at once). If that snapshot is terminal, the stream is
     * completed right away.
     */
    public void register(UUID jobId, SseEmitter emitter, JobStatusResponse initialSnapshot) {
        Set<SseEmitter> forJob = subscribers.computeIfAbsent(jobId, id -> ConcurrentHashMap.newKeySet());
        forJob.add(emitter);
        // Client-initiated closes (browser navigates away, timeout) deregister via these callbacks;
        // server-initiated terminal closes below remove explicitly (a completed emitter with no
        // active request won't fire the callback synchronously).
        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(() -> remove(jobId, emitter));
        emitter.onError(e -> remove(jobId, emitter));

        boolean delivered = trySend(emitter, initialSnapshot);
        if (!delivered || initialSnapshot.status().isTerminal()) {
            completeQuietly(emitter);
            remove(jobId, emitter);
        }
    }

    /** Poke handler: snapshot the job and push to its subscribers; close them if terminal. */
    public void onJobEvent(UUID jobId) {
        Set<SseEmitter> forJob = subscribers.get(jobId);
        if (forJob == null || forJob.isEmpty()) {
            return; // nobody watching this job on this instance
        }
        JobStatusResponse snapshot = jobStatusService.getStatus(jobId);
        boolean terminal = snapshot.status().isTerminal();
        for (SseEmitter emitter : forJob) {
            boolean delivered = trySend(emitter, snapshot);
            if (!delivered || terminal) {
                completeQuietly(emitter);
                remove(jobId, emitter);
            }
        }
    }

    /** Package-visible for tests: are any subscribers currently registered for this job? */
    boolean hasEmitters(UUID jobId) {
        Set<SseEmitter> forJob = subscribers.get(jobId);
        return forJob != null && !forJob.isEmpty();
    }

    private boolean trySend(SseEmitter emitter, JobStatusResponse snapshot) {
        try {
            emitter.send(SseEmitter.event().name("status").data(snapshot));
            return true;
        } catch (IOException | IllegalStateException e) {
            // client gone / emitter already completed
            log.debug("SSE send failed, dropping emitter: {}", e.toString());
            return false;
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException e) {
            log.debug("SSE complete failed: {}", e.toString());
        }
    }

    private void remove(UUID jobId, SseEmitter emitter) {
        Set<SseEmitter> forJob = subscribers.get(jobId);
        if (forJob != null) {
            forJob.remove(emitter);
            if (forJob.isEmpty()) {
                subscribers.remove(jobId, forJob); // drop the now-empty set (value-guarded)
            }
        }
    }
}
