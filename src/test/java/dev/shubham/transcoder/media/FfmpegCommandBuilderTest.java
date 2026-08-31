package dev.shubham.transcoder.media;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic test of ffmpeg argv assembly (each token a separate element).
 */
class FfmpegCommandBuilderTest {

    @Test
    void assemblesPrefixInputArgsAndOutputInOrder() {
        Path input = Path.of("in.mp4");
        List<String> command = FfmpegCommandBuilder.ffmpeg()
                .input(input)
                .args("-c", "copy", "-f", "segment")
                .output("seg_%05d.ts")
                .build();

        assertEquals(List.of(
                "ffmpeg", "-hide_banner", "-nostdin", "-y",
                "-i", input.toString(),
                "-c", "copy", "-f", "segment",
                "seg_%05d.ts"), command);
    }
}
