package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.config.PipelineProperties;
import dev.shubham.transcoder.messaging.PackageTask;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.SegmentRepository;
import org.springframework.stereotype.Service;

/**
 * Stage-3 orchestration + state for one rung: download that rung's encoded segments, delegate
 * to the {@link Packager} resolved from {@code pipeline.output-mode}, upload the outputs, mark
 * the rung done, and — when every rung is done — flip the job to {@code COMPLETED} via a final
 * guarded update (calling {@link Packager#finalizeJob} for the master manifest).
 *
 * <p>Resolves the strategy through {@link PackagerFactory}; it never branches on the mode
 * itself. Transport is handled by {@link PackageListener}.
 */
@Service
public class PackageHandler {

    private final PackagerFactory packagerFactory;
    private final PipelineProperties pipelineProperties;
    private final BlobStore blobStore;
    private final SegmentRepository segmentRepository;

    public PackageHandler(PackagerFactory packagerFactory,
                          PipelineProperties pipelineProperties,
                          BlobStore blobStore,
                          SegmentRepository segmentRepository) {
        this.packagerFactory = packagerFactory;
        this.pipelineProperties = pipelineProperties;
        this.blobStore = blobStore;
        this.segmentRepository = segmentRepository;
    }

    public void packageRung(PackageTask task) {
        OutputMode mode = OutputMode.fromConfig(pipelineProperties.outputMode());
        Packager packager = packagerFactory.forMode(mode);
        // TODO download rung segments -> packager.packageRung(...) -> mark rung done ->
        // TODO when all rungs done: packager.finalizeJob(...) and job -> COMPLETED.
        throw new UnsupportedOperationException("not implemented");
    }
}
