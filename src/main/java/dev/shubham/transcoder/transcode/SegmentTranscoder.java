package dev.shubham.transcoder.transcode;

import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Wraps the FFmpeg encode of a single segment to one rung
 * ({@code -vf scale}, {@code -c:v libx264}, target bitrate/CRF).
 */
@Service
public class SegmentTranscoder {

    /** @return the encoded output file. */
    public Path encode(Path segmentFile, Rung rung) {
        // TODO ffmpeg scale + encode to the rung's height/bitrate.
        throw new UnsupportedOperationException("not implemented");
    }
}
