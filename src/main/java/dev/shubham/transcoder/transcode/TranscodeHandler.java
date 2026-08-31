package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.messaging.TranscodeTask;
import dev.shubham.transcoder.storage.BlobStore;
import org.springframework.stereotype.Service;

/**
 * Stage-2 orchestration + state for one segment: mark PROCESSING → download → FFmpeg encode →
 * upload under a deterministic key → mark DONE and run the atomic per-rung fan-in claim. The
 * winning claimant enqueues the package task for its rung. Idempotent: a redelivery overwrites
 * the same output key. Transport is handled by {@link TranscodeListener}.
 */
@Service
public class TranscodeHandler {

    private final Transcoder transcoder;
    private final BlobStore blobStore;
    private final SegmentRepository segmentRepository;

    public TranscodeHandler(Transcoder transcoder,
                            BlobStore blobStore,
                            SegmentRepository segmentRepository) {
        this.transcoder = transcoder;
        this.blobStore = blobStore;
        this.segmentRepository = segmentRepository;
    }

    public void transcode(TranscodeTask task) {
        // TODO PROCESSING -> download -> encode -> upload -> markDone -> tryClaimPackaging -> maybe publish PackageTask.
        throw new UnsupportedOperationException("not implemented");
    }
}
