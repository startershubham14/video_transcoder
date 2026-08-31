package dev.shubham.transcoder.job;

import dev.shubham.transcoder.storage.BlobStore;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Bridges completed jobs to connected clients. Storage of outputs is unconditional
 * (always written to S3); only <em>delivery</em> branches — push over SSE when a client
 * is connected, otherwise the client pulls on next poll. Presigned GET URLs are minted
 * on demand (via {@link BlobStore}) and never stored.
 */
@Service
public class OutputDeliveryService {

    private final BlobStore blobStore;

    public OutputDeliveryService(BlobStore blobStore) {
        this.blobStore = blobStore;
    }

    /** Register an SSE subscriber for a job. */
    public void register(UUID jobId, SseEmitter emitter) {
        // TODO track emitter per job.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Push output URLs to a connected client on completion (no-op if none connected). */
    public void publishCompletion(UUID jobId) {
        // TODO mint presigned GETs via blobStore and emit to any subscriber.
        throw new UnsupportedOperationException("not implemented");
    }
}
