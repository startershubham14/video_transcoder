package dev.shubham.transcoder.transcode;

import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * FFmpeg adapter for {@link Transcoder}. Encodes a single segment to one rung
 * ({@code -vf scale}, {@code -c:v libx264}, target bitrate/CRF).
 */
@Service
public class FfmpegTranscoder implements Transcoder {

    /** @return the encoded output file. */
    @Override
    public Path encode(Path segmentFile, Rung rung) {
        // TODO ffmpeg scale + encode to the rung's height/bitrate.
        // TODO assemble args via a shared FfmpegCommandBuilder rather than string concat.
        throw new UnsupportedOperationException("not implemented");
    }
}
