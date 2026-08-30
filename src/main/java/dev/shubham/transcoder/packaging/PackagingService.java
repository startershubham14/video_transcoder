package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.messaging.PackageTask;
import org.springframework.stereotype.Service;

/**
 * Stage-3 orchestration for one rung: download that rung's encoded segments, concat to
 * MP4 or package to HLS per {@code pipeline.output-mode}, upload the outputs (plus the
 * master {@code .m3u8} for HLS), mark the rung done, and — when every rung is done —
 * flip the job to {@code COMPLETED} via a final guarded update.
 */
@Service
public class PackagingService {

    private final Mp4Concatenator mp4Concatenator;
    private final HlsPackager hlsPackager;

    public PackagingService(Mp4Concatenator mp4Concatenator, HlsPackager hlsPackager) {
        this.mp4Concatenator = mp4Concatenator;
        this.hlsPackager = hlsPackager;
    }

    public void packageRung(PackageTask task) {
        // TODO download rung segments -> concat/package -> upload -> mark rung done -> maybe COMPLETED.
        throw new UnsupportedOperationException("not implemented");
    }
}
