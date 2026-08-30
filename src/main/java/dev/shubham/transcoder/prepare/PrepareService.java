package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.messaging.PrepareTask;
import org.springframework.stereotype.Service;

/**
 * Stage-1 orchestration: download source → ffprobe (validity, real duration/size) →
 * enforce input limits → ClamAV scan → derive the output ladder → keyframe-aligned split →
 * insert segment rows per rung → commit {@code PROCESSING} → fan out transcode tasks.
 *
 * <p>Any of the guard steps failing transitions the job to {@code FAILED} with a reason.
 */
@Service
public class PrepareService {

    private final FfprobeService ffprobeService;
    private final ClamAvScanner clamAvScanner;
    private final VideoSplitter videoSplitter;
    private final OutputLadderService outputLadderService;

    public PrepareService(FfprobeService ffprobeService,
                          ClamAvScanner clamAvScanner,
                          VideoSplitter videoSplitter,
                          OutputLadderService outputLadderService) {
        this.ffprobeService = ffprobeService;
        this.clamAvScanner = clamAvScanner;
        this.videoSplitter = videoSplitter;
        this.outputLadderService = outputLadderService;
    }

    public void prepare(PrepareTask task) {
        // TODO probe -> limit checks -> scan -> ladder -> split -> insert segments -> fan-out.
        throw new UnsupportedOperationException("not implemented");
    }
}
