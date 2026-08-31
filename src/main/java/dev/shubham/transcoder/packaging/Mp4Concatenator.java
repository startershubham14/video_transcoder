package dev.shubham.transcoder.packaging;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * MP4 milestone: stitch one rung's encoded segments (in index order) into a single
 * standalone {@code {rung}.mp4} using the FFmpeg concat demuxer.
 */
@Service
public class Mp4Concatenator {

    /** @return the concatenated MP4 file. */
    public Path concat(List<Path> orderedSegments) {
        // TODO ffmpeg concat demuxer -> one MP4.
        throw new UnsupportedOperationException("not implemented");
    }
}
