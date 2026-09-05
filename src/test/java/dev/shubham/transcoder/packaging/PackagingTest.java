package dev.shubham.transcoder.packaging;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic tests for packaging: deterministic output keys, concat list + argv.
 */
class PackagingTest {

    private static final UUID JOB = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    @Test
    void mp4OutputKeyIsJobSlashRung() {
        assertEquals(JOB + "/720p.mp4", new Mp4Packager(null, null).outputKey(JOB, "720p"));
    }

    @Test
    void hlsOutputKeyIsMediaPlaylist() {
        assertEquals(JOB + "/hls/480p.m3u8", new HlsPackager(null, null).outputKey(JOB, "480p"));
    }

    @Test
    void concatListHasOneFileLinePerSegmentInOrder() {
        Path a = Path.of("0.ts");
        Path b = Path.of("1.ts");
        String expected = "file '" + a.toAbsolutePath() + "'\n"
                + "file '" + b.toAbsolutePath() + "'\n";
        assertEquals(expected, Mp4Packager.concatList(List.of(a, b)));
    }

    @Test
    void concatCommandUsesConcatDemuxerStreamCopy() {
        Path list = Path.of("concat.txt");
        Path out = Path.of("720p.mp4");
        assertEquals(List.of(
                "ffmpeg", "-hide_banner", "-nostdin", "-y",
                "-f", "concat", "-safe", "0",
                "-i", list.toString(),
                "-c", "copy",
                out.toString()), Mp4Packager.concatCommand(list, out));
    }
}
