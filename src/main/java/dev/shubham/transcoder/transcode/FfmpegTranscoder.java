package dev.shubham.transcoder.transcode;

import dev.shubham.transcoder.media.FfmpegCommandBuilder;
import dev.shubham.transcoder.media.ProcessRunner;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * FFmpeg adapter for {@link Transcoder}. Scales one segment down to a rung and re-encodes to
 * H.264/AAC in an MPEG-TS container (concat-friendly for the packaging stage).
 */
@Service
public class FfmpegTranscoder implements Transcoder {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final ProcessRunner processRunner;

    public FfmpegTranscoder(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public Path encode(Path segmentFile, Rung rung) {
        Path output = segmentFile.resolveSibling("encoded-" + rung.label() + ".ts");
        processRunner.run(encodeCommand(segmentFile, rung, output), TIMEOUT);
        return output;
    }

    /** Build the ffmpeg argv for one segment encode. Package-visible for unit testing. */
    static List<String> encodeCommand(Path input, Rung rung, Path output) {
        return FfmpegCommandBuilder.ffmpeg()
                .input(input)
                .args("-vf", "scale=-2:" + rung.height(),
                        "-c:v", "libx264", "-preset", "veryfast", "-b:v", rung.videoBitrate(),
                        "-c:a", "aac", "-b:a", "128k",
                        "-f", "mpegts")
                .output(output.toString())
                .build();
    }
}
