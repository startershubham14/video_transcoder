package dev.shubham.transcoder.packaging;

import dev.shubham.transcoder.prepare.MediaProbe;
import dev.shubham.transcoder.storage.BlobStore;
import dev.shubham.transcoder.transcode.Rung;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * HLS goal strategy: package a rung's already-transcoded MPEG-TS segments into a VOD media
 * {@code .m3u8}, and (in {@link #finalizeJob}) assemble one master {@code .m3u8} listing all rungs
 * by bandwidth. The transcode stage already produced the {@code .ts} chunks
 * ({@code {jobId}/{rung}/{index}.ts}); this stage only writes playlists that reference them — the
 * whole point of the {@link Packager} seam is that MP4 → HLS changes only the packaging step, not
 * the expensive transcode work.
 *
 * <p>Playlists reference their targets <em>relatively</em>, so HLS delivery is served public-read
 * from object storage (see {@code JobStatusService} / docker-compose) rather than via presigned URLs.
 */
@Component
public class HlsPackager implements Packager {

    private static final String AUDIO_BITRATE_BPS_STR = "128k"; // matches FfmpegTranscoder's -b:a
    private static final int AUDIO_BITRATE_BPS = parseBitrate(AUDIO_BITRATE_BPS_STR);

    private final BlobStore blobStore;
    private final MediaProbe mediaProbe;

    public HlsPackager(BlobStore blobStore, MediaProbe mediaProbe) {
        this.blobStore = blobStore;
        this.mediaProbe = mediaProbe;
    }

    @Override
    public OutputMode mode() {
        return OutputMode.HLS;
    }

    @Override
    public String outputKey(UUID jobId, String rung) {
        return jobId + "/hls/" + rung + ".m3u8";
    }

    @Override
    public Optional<String> masterOutputKey(UUID jobId) {
        return Optional.of(jobId + "/master.m3u8");
    }

    @Override
    public void packageRung(UUID jobId, String rung, List<Path> orderedSegments) {
        if (orderedSegments.isEmpty()) {
            throw new IllegalStateException("no segments to package for rung " + rung);
        }
        List<SegmentEntry> entries = new ArrayList<>(orderedSegments.size());
        for (Path segment : orderedSegments) {
            BigDecimal duration = mediaProbe.probe(segment).durationSeconds();
            // The .ts already lives at {jobId}/{rung}/{index}.ts; the media playlist sits at
            // {jobId}/hls/{rung}.m3u8, so reference it back out of the hls/ prefix.
            String uri = "../" + rung + "/" + segment.getFileName();
            entries.add(new SegmentEntry(uri, duration));
        }
        Path workDir = orderedSegments.get(0).getParent();
        Path playlist = workDir.resolve(rung + ".m3u8");
        writeString(playlist, mediaPlaylist(entries));
        blobStore.upload(playlist, outputKey(jobId, rung));
    }

    @Override
    public void finalizeJob(UUID jobId, List<String> rungs) {
        List<Variant> variants = new ArrayList<>(rungs.size());
        for (String rungLabel : rungs) {
            Rung rung = Rung.fromLabel(rungLabel);
            variants.add(new Variant("hls/" + rungLabel + ".m3u8", bandwidthOf(rung), rung.height()));
        }
        variants.sort(Comparator.comparingInt(Variant::bandwidth).reversed()); // highest first

        Path tmp;
        try {
            tmp = Files.createTempFile("master-" + jobId + "-", ".m3u8");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            writeString(tmp, masterPlaylist(variants));
            blobStore.upload(tmp, jobId + "/master.m3u8");
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort scratch cleanup
            }
        }
    }

    // --- pure playlist authoring (package-visible for unit testing) ---

    /** One media-playlist entry: a segment URI and its duration in seconds. */
    record SegmentEntry(String uri, BigDecimal durationSeconds) {
    }

    /** One master-playlist variant: media-playlist URI, bandwidth (bps), and height. */
    record Variant(String uri, int bandwidth, int height) {
    }

    /** VOD media playlist referencing the rung's ordered segments. */
    static String mediaPlaylist(List<SegmentEntry> segments) {
        int targetDuration = segments.stream()
                .map(SegmentEntry::durationSeconds)
                .mapToInt(d -> (int) Math.ceil(d.doubleValue()))
                .max().orElse(1);
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n")
                .append("#EXT-X-VERSION:3\n")
                .append("#EXT-X-TARGETDURATION:").append(Math.max(1, targetDuration)).append('\n')
                .append("#EXT-X-MEDIA-SEQUENCE:0\n")
                .append("#EXT-X-PLAYLIST-TYPE:VOD\n");
        for (SegmentEntry segment : segments) {
            sb.append("#EXTINF:").append(String.format("%.6f", segment.durationSeconds().doubleValue()))
                    .append(",\n").append(segment.uri()).append('\n');
        }
        sb.append("#EXT-X-ENDLIST\n");
        return sb.toString();
    }

    /** Master playlist listing each rung variant by bandwidth. */
    static String masterPlaylist(List<Variant> variants) {
        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n").append("#EXT-X-VERSION:3\n");
        for (Variant variant : variants) {
            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(variant.bandwidth())
                    .append(",RESOLUTION=").append(nominalWidth(variant.height())).append('x').append(variant.height())
                    .append('\n').append(variant.uri()).append('\n');
        }
        return sb.toString();
    }

    /** Advertised bandwidth for a rung: video bitrate + the fixed audio bitrate, in bits/sec. */
    static int bandwidthOf(Rung rung) {
        return parseBitrate(rung.videoBitrate()) + AUDIO_BITRATE_BPS;
    }

    /** Parse an ffmpeg bitrate string ({@code "2500k"}, {@code "1M"}) into bits per second. */
    static int parseBitrate(String bitrate) {
        String value = bitrate.trim();
        char suffix = value.charAt(value.length() - 1);
        int multiplier = switch (Character.toLowerCase(suffix)) {
            case 'k' -> 1_000;
            case 'm' -> 1_000_000;
            default -> 1;
        };
        String number = Character.isDigit(suffix) ? value : value.substring(0, value.length() - 1);
        return Integer.parseInt(number.trim()) * multiplier;
    }

    /** Nominal 16:9 width for a height (even), for the master playlist RESOLUTION hint. */
    private static int nominalWidth(int height) {
        int width = Math.round(height * 16f / 9f);
        return width % 2 == 0 ? width : width + 1;
    }

    private static void writeString(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
