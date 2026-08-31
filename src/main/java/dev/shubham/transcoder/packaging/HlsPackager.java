package dev.shubham.transcoder.packaging;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * HLS goal: package a rung's segments into {@code .ts} chunks + a per-rung media
 * {@code .m3u8}, and assemble one master {@code .m3u8} listing all rungs by bandwidth.
 * Mostly writes playlists referencing segments that already exist.
 */
@Service
public class HlsPackager {

    /** Package one rung; returns the media playlist file. */
    public Path packageRung(String rung, List<Path> orderedSegments) {
        // TODO write .ts + media .m3u8 for the rung.
        throw new UnsupportedOperationException("not implemented");
    }

    /** Write the master playlist referencing every rung's media playlist. */
    public Path writeMasterPlaylist(List<String> rungs) {
        // TODO write master .m3u8 (bandwidth/resolution per variant).
        throw new UnsupportedOperationException("not implemented");
    }
}
