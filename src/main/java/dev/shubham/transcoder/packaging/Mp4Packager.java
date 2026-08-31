package dev.shubham.transcoder.packaging;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * MP4 milestone strategy: stitch one rung's encoded segments (in index order) into a
 * single standalone {@code {rung}.mp4} via the FFmpeg concat demuxer. Plays in any
 * {@code <video>} tag — proves the pipeline end-to-end. No job-level finalization.
 */
@Component
public class Mp4Packager implements Packager {

    @Override
    public OutputMode mode() {
        return OutputMode.MP4;
    }

    @Override
    public void packageRung(UUID jobId, String rung, List<Path> orderedSegments) {
        // TODO ffmpeg concat demuxer -> one MP4; upload under the rung's output key.
        throw new UnsupportedOperationException("not implemented");
    }
}
