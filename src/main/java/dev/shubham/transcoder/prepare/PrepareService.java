package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.messaging.PrepareTask;
import dev.shubham.transcoder.storage.BlobStore;
import org.springframework.stereotype.Service;

/**
 * Stage-1 orchestration: download source → ffprobe (validity, real duration/size) →
 * enforce input limits → ClamAV scan → derive the output ladder → keyframe-aligned split →
 * insert segment rows per rung → commit {@code PROCESSING} → fan out transcode tasks.
 *
 * <p>Depends on the ports ({@link MediaProbe}, {@link VirusScanner}, {@link Splitter},
 * {@link BlobStore}), never the vendor tools directly. Any guard step failing transitions
 * the job to {@code FAILED} with a reason.
 */
@Service
public class PrepareService {

    private final BlobStore blobStore;
    private final MediaProbe mediaProbe;
    private final VirusScanner virusScanner;
    private final Splitter splitter;
    private final OutputLadderService outputLadderService;

    public PrepareService(BlobStore blobStore,
                          MediaProbe mediaProbe,
                          VirusScanner virusScanner,
                          Splitter splitter,
                          OutputLadderService outputLadderService) {
        this.blobStore = blobStore;
        this.mediaProbe = mediaProbe;
        this.virusScanner = virusScanner;
        this.splitter = splitter;
        this.outputLadderService = outputLadderService;
    }

    public void prepare(PrepareTask task) {
        // TODO download -> probe -> limit checks -> scan -> ladder -> split -> insert segments -> fan-out.
        // TODO consider a validation Chain (probe-valid, within-limits, clean) as guards multiply.
        throw new UnsupportedOperationException("not implemented");
    }
}
