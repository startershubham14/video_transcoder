package dev.shubham.transcoder.media;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds an {@code ffmpeg} argument vector — no ad-hoc string concatenation of args (each
 * token is a separate list element, so paths with spaces are safe). Starts with
 * {@code ffmpeg -hide_banner -nostdin -y}; callers add the input, options, and output. Reused
 * by the splitter now and the transcoder later.
 *
 * <pre>{@code
 * FfmpegCommandBuilder.ffmpeg()
 *     .input(source)
 *     .args("-c", "copy", "-f", "segment", "-segment_time", "8")
 *     .output(pattern)
 *     .build();
 * }</pre>
 */
public final class FfmpegCommandBuilder {

    private final List<String> args = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-nostdin", "-y"));

    private FfmpegCommandBuilder() {
    }

    public static FfmpegCommandBuilder ffmpeg() {
        return new FfmpegCommandBuilder();
    }

    public FfmpegCommandBuilder input(Path input) {
        args.add("-i");
        args.add(input.toString());
        return this;
    }

    public FfmpegCommandBuilder arg(String value) {
        args.add(value);
        return this;
    }

    public FfmpegCommandBuilder args(String... values) {
        for (String v : values) {
            args.add(v);
        }
        return this;
    }

    /** The output target (file or segment pattern) — added last. */
    public FfmpegCommandBuilder output(String target) {
        args.add(target);
        return this;
    }

    public List<String> build() {
        return List.copyOf(args);
    }
}
