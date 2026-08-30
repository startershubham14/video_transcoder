package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.messaging.TranscodeTask;
import org.springframework.stereotype.Service;

/**
 * Stage-2 orchestration for one segment: mark PROCESSING → download → FFmpeg encode →
 * upload under a deterministic key → mark DONE and run the atomic per-rung fan-in claim.
 * The winning claimant enqueues the package task for its rung. Idempotent: a redelivery
 * overwrites the same output key.
 */
@Service
public class TranscodeService {

    private final SegmentTranscoder segmentTranscoder;
    private final SegmentRepository segmentRepository;

    public TranscodeService(SegmentTranscoder segmentTranscoder, SegmentRepository segmentRepository) {
        this.segmentTranscoder = segmentTranscoder;
        this.segmentRepository = segmentRepository;
    }

    public void transcode(TranscodeTask task) {
        // TODO PROCESSING -> download -> encode -> upload -> markDone -> claimRungCompletion -> maybe publish PackageTask.
        throw new UnsupportedOperationException("not implemented");
    }
}
