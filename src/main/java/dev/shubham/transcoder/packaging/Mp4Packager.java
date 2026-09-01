package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.media.FfmpegCommandBuilder;
import dev.shubham.transcoder.media.ProcessRunner;
import dev.shubham.transcoder.storage.BlobStore;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MP4 milestone strategy: stitch one rung's encoded segments (in index order) into a single
 * standalone {@code {jobId}/{rung}.mp4} via the FFmpeg concat demuxer ({@code -c copy}, no
 * re-encode). Plays in any {@code <video>} tag — proves the pipeline end-to-end. No job-level
 * finalization (MP4 has no master manifest).
 */
@Component
public class Mp4Packager implements Packager {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final BlobStore blobStore;
    private final ProcessRunner processRunner;

    public Mp4Packager(BlobStore blobStore, ProcessRunner processRunner) {
        this.blobStore = blobStore;
        this.processRunner = processRunner;
    }

    @Override
    public OutputMode mode() {
        return OutputMode.MP4;
    }

    @Override
    public String outputKey(UUID jobId, String rung) {
        return jobId + "/" + rung + ".mp4";
    }

    @Override
    public void packageRung(UUID jobId, String rung, List<Path> orderedSegments) {
        if (orderedSegments.isEmpty()) {
            throw new IllegalStateException("no segments to package for rung " + rung);
        }
        Path workDir = orderedSegments.get(0).getParent();
        Path listFile = workDir.resolve("concat-" + rung + ".txt");
        Path output = workDir.resolve(rung + ".mp4");
        try {
            Files.writeString(listFile, concatList(orderedSegments), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        processRunner.run(concatCommand(listFile, output), TIMEOUT);
        blobStore.upload(output, outputKey(jobId, rung));
    }

    /** FFmpeg concat-demuxer list body: one {@code file '<path>'} line per segment, in order. */
    static String concatList(List<Path> orderedSegments) {
        return orderedSegments.stream()
                .map(p -> "file '" + p.toAbsolutePath() + "'")
                .collect(Collectors.joining("\n", "", "\n"));
    }

    /** ffmpeg concat-demuxer argv (stream copy). Package-visible for unit testing. */
    static List<String> concatCommand(Path listFile, Path output) {
        return FfmpegCommandBuilder.ffmpeg()
                .args("-f", "concat", "-safe", "0")
                .args("-i", listFile.toString())
                .args("-c", "copy")
                .output(output.toString())
                .build();
    }
}
