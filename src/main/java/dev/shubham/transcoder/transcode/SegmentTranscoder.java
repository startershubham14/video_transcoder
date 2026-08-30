package dev.shubham.transcoder.transcode;

import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Wraps the FFmpeg encode of a single segment to one rung
 * ({@code -vf scale}, {@code -c:v libx264}, target bitrate/CRF). The {@link Transcoder}
 * adapter.
 */
@Service
public class SegmentTranscoder implements Transcoder {

    /** @return the encoded output file. */
    @Override
    public Path encode(Path segmentFile, Rung rung) {
        // TODO ffmpeg scale + encode to the rung's height/bitrate.
        // TODO assemble args via a shared FfmpegCommandBuilder rather than string concat.
        throw new UnsupportedOperationException("not implemented");
    }
}
