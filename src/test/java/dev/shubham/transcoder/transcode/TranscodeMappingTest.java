package dev.shubham.transcoder.transcode;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure-logic tests for the transcode stage: rung lookup, encode argv, output-key derivation.
 */
class TranscodeMappingTest {

    @Test
    void rungFromLabelResolvesAndRejectsUnknown() {
        assertEquals(Rung.R720P, Rung.fromLabel("720p"));
        assertEquals(Rung.R360P, Rung.fromLabel("360p"));
        assertThrows(IllegalArgumentException.class, () -> Rung.fromLabel("1080p"));
    }

    @Test
    void encodeCommandScalesToRungHeightAndBitrate() {
        Path in = Path.of("segment.ts");
        Path out = Path.of("encoded.ts");
        List<String> command = FfmpegTranscoder.encodeCommand(in, Rung.R480P, out);

        assertEquals(List.of(
                "ffmpeg", "-hide_banner", "-nostdin", "-y",
                "-i", in.toString(),
                "-vf", "scale=-2:480",
                "-c:v", "libx264", "-preset", "veryfast", "-b:v", "1000k",
                "-c:a", "aac", "-b:a", "128k",
                "-f", "mpegts",
                out.toString()), command);
    }

    @Test
    void outputKeyIsDeterministicPerJobRungIndex() {
        UUID jobId = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
        assertEquals(jobId + "/720p/3.ts", TranscodeHandler.outputKey(jobId, "720p", 3));
    }
}
