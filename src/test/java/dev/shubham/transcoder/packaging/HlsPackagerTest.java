package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.packaging.HlsPackager.SegmentEntry;
import dev.shubham.transcoder.packaging.HlsPackager.Variant;
import dev.shubham.transcoder.prepare.MediaProbe;
import dev.shubham.transcoder.prepare.ProbeResult;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.Rung;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for HLS packaging: pure playlist authoring + bandwidth math, key shapes, and that
 * packageRung uploads the media playlist under the derived key (the .ts already exist).
 */
class HlsPackagerTest {

    // --- bandwidth parsing ---

    @Test
    void bandwidthIsVideoPlusAudio() {
        assertEquals(2_628_000, HlsPackager.bandwidthOf(Rung.R720P)); // 2500k + 128k
        assertEquals(1_128_000, HlsPackager.bandwidthOf(Rung.R480P)); // 1000k + 128k
        assertEquals(728_000, HlsPackager.bandwidthOf(Rung.R360P));   // 600k + 128k
    }

    @Test
    void parsesBitrateSuffixes() {
        assertEquals(2_500_000, HlsPackager.parseBitrate("2500k"));
        assertEquals(1_000_000, HlsPackager.parseBitrate("1M"));
        assertEquals(600_000, HlsPackager.parseBitrate("600k"));
    }

    // --- media playlist ---

    @Test
    void mediaPlaylistIsWellFormed() {
        String playlist = HlsPackager.mediaPlaylist(List.of(
                new SegmentEntry("../720p/0.ts", new BigDecimal("6.0")),
                new SegmentEntry("../720p/1.ts", new BigDecimal("4.5"))));

        assertTrue(playlist.startsWith("#EXTM3U\n"), playlist);
        assertTrue(playlist.contains("#EXT-X-PLAYLIST-TYPE:VOD"), playlist);
        assertTrue(playlist.contains("#EXT-X-TARGETDURATION:6"), playlist); // ceil(max 6.0)
        assertTrue(playlist.contains("#EXTINF:6.000000,\n../720p/0.ts\n"), playlist);
        assertTrue(playlist.contains("#EXTINF:4.500000,\n../720p/1.ts\n"), playlist);
        assertTrue(playlist.trim().endsWith("#EXT-X-ENDLIST"), playlist);
    }

    // --- master playlist ---

    @Test
    void masterPlaylistListsEachVariant() {
        String master = HlsPackager.masterPlaylist(List.of(
                new Variant("hls/720p.m3u8", 2_628_000, 720),
                new Variant("hls/480p.m3u8", 728_000, 480)));

        assertTrue(master.startsWith("#EXTM3U\n"), master);
        assertTrue(master.contains("#EXT-X-STREAM-INF:BANDWIDTH=2628000,RESOLUTION=1280x720\nhls/720p.m3u8\n"), master);
        assertTrue(master.contains("#EXT-X-STREAM-INF:BANDWIDTH=728000,RESOLUTION=854x480\nhls/480p.m3u8\n"), master);
    }

    // --- keys ---

    @Test
    void keyShapes() {
        UUID id = UUID.randomUUID();
        HlsPackager packager = new HlsPackager(mock(BlobStore.class), mock(MediaProbe.class));
        assertEquals(id + "/hls/720p.m3u8", packager.outputKey(id, "720p"));
        assertEquals(id + "/master.m3u8", packager.masterOutputKey(id).orElseThrow());
    }

    // --- packageRung uploads the playlist under the derived key ---

    @Test
    void packageRungUploadsMediaPlaylistUnderDerivedKey(@TempDir Path dir) throws IOException {
        BlobStore blobStore = mock(BlobStore.class);
        MediaProbe mediaProbe = mock(MediaProbe.class);
        when(mediaProbe.probe(any())).thenReturn(
                new ProbeResult(1280, 720, new BigDecimal("6.0"), new BigDecimal("30"), "h264", 100L));

        Path s0 = Files.createFile(dir.resolve("0.ts"));
        Path s1 = Files.createFile(dir.resolve("1.ts"));
        UUID id = UUID.randomUUID();

        new HlsPackager(blobStore, mediaProbe).packageRung(id, "720p", List.of(s0, s1));

        verify(blobStore).upload(any(Path.class), eq(id + "/hls/720p.m3u8"));
    }
}
