package dev.shubham.transcoder.prepare;

import dev.shubham.transcoder.media.FfmpegCommandBuilder;
import dev.shubham.transcoder.media.ProcessRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * FFmpeg adapter for {@link Splitter}. Splits the source into <em>keyframe-aligned</em> MPEG-TS
 * segments with the segment muxer and {@code -c copy} (no re-encode) — the real
 * parallel-processing unit. Target length is a target: the muxer snaps to keyframes, so
 * segment durations are uneven.
 */
@Service
public class FfmpegSplitter implements Splitter {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final ProcessRunner processRunner;

    public FfmpegSplitter(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public List<Path> split(Path sourceFile, int targetSeconds) {
        try {
            Path outDir = sourceFile.resolveSibling("segments");
            Files.createDirectories(outDir);
            String pattern = outDir.resolve("seg_%05d.ts").toString();

            List<String> command = FfmpegCommandBuilder.ffmpeg()
                    .input(sourceFile)
                    .args("-c", "copy", "-map", "0",
                            "-f", "segment", "-segment_format", "mpegts",
                            "-segment_time", String.valueOf(targetSeconds),
                            "-reset_timestamps", "1")
                    .output(pattern)
                    .build();
            processRunner.run(command, TIMEOUT);

            try (Stream<Path> files = Files.list(outDir)) {
                return files
                        .filter(path -> path.getFileName().toString().endsWith(".ts"))
                        .sorted() // seg_00000.ts, seg_00001.ts, … — playback order
                        .toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
