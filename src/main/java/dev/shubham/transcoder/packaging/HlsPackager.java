package dev.shubham.transcoder.packaging;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * HLS goal strategy: package a rung's segments into {@code .ts} chunks + a per-rung media
 * {@code .m3u8}, and (in {@link #finalizeJob}) assemble one master {@code .m3u8} listing
 * all rungs by bandwidth. Mostly writes playlists referencing segments that already exist.
 */
@Component
public class HlsPackager implements Packager {

    @Override
    public OutputMode mode() {
        return OutputMode.HLS;
    }

    @Override
    public void packageRung(UUID jobId, String rung, List<Path> orderedSegments) {
        // TODO write .ts + media .m3u8 for the rung; upload outputs.
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void finalizeJob(UUID jobId, List<String> rungs) {
        // TODO write master .m3u8 (bandwidth/resolution per variant); set jobs.manifest_key.
        throw new UnsupportedOperationException("not implemented");
    }
}
