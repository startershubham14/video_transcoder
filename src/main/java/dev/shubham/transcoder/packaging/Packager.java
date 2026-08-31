package dev.shubham.transcoder.packaging;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Strategy for the final packaging step — the pipeline's primary open/closed seam.
 * The expensive 90% (upload → probe → scan → split → transcode) is identical across
 * output modes; only this last step differs, so MP4 → HLS is a new implementation, not a
 * rewrite. Callers resolve an implementation via {@link PackagerFactory} and never branch
 * on {@link OutputMode} themselves.
 *
 * @see Mp4Packager
 * @see HlsPackager
 */
public interface Packager {

    /** Which output mode this strategy handles. Used by the factory to register it. */
    OutputMode mode();

    /** Concat/package one rung's ordered segments and write its output(s) to storage. */
    void packageRung(UUID jobId, String rung, List<Path> orderedSegments);

    /**
     * Job-level finalization once every rung is packaged. No-op for MP4; HLS overrides
     * this to write the master {@code .m3u8} referencing each rung's media playlist.
     */
    default void finalizeJob(UUID jobId, List<String> rungs) {
        // no-op by default
    }
}
