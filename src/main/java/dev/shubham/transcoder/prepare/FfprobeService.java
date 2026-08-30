package dev.shubham.transcoder.prepare;

import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Wraps {@code ffprobe} to extract real dimensions/duration/codec/bitrate. A probe
 * failure means invalid/corrupt input — the job is failed rather than handed to FFmpeg.
 * The {@link MediaProbe} adapter.
 */
@Service
public class FfprobeService implements MediaProbe {

    @Override
    public ProbeResult probe(Path sourceFile) {
        // TODO run: ffprobe -v quiet -print_format json -show_format -show_streams <file>; parse.
        throw new UnsupportedOperationException("not implemented");
    }
}
