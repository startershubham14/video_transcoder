package dev.shubham.transcoder.prepare;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shubham.transcoder.media.ProcessExecutionException;
import dev.shubham.transcoder.media.ProcessResult;
import dev.shubham.transcoder.media.ProcessRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * ffprobe adapter for {@link MediaProbe}. A probe failure or a file with no video stream is a
 * permanent, input-caused rejection ({@link PrepareRejectedException}) — the job is failed, not
 * retried, rather than handed to FFmpeg.
 */
@Service
public class FfprobeMediaProbe implements MediaProbe {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ProcessRunner processRunner;

    public FfprobeMediaProbe(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Override
    public ProbeResult probe(Path sourceFile) {
        List<String> command = List.of(
                "ffprobe", "-v", "quiet", "-print_format", "json",
                "-show_format", "-show_streams", sourceFile.toString());
        ProcessResult result;
        try {
            result = processRunner.run(command, TIMEOUT);
        } catch (ProcessExecutionException e) {
            throw new PrepareRejectedException("invalid or unreadable media");
        }
        long sizeBytes;
        try {
            sizeBytes = Files.size(sourceFile);
        } catch (IOException e) {
            throw new PrepareRejectedException("cannot read the uploaded source");
        }
        return parse(result.stdout(), sizeBytes);
    }

    /** Parse ffprobe JSON into {@link ProbeResult}. Package-visible for unit testing. */
    static ProbeResult parse(String json, long sizeBytes) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new PrepareRejectedException("unparseable ffprobe output");
        }
        JsonNode video = null;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText())) {
                video = stream;
                break;
            }
        }
        if (video == null) {
            throw new PrepareRejectedException("no video stream in input");
        }
        int width = video.path("width").asInt();
        int height = video.path("height").asInt();
        String codec = video.path("codec_name").asText("");
        BigDecimal fps = parseFrameRate(video.path("avg_frame_rate").asText("0/1"));
        BigDecimal duration = parseDecimal(root.path("format").path("duration").asText("0"));
        return new ProbeResult(width, height, duration, fps, codec, sizeBytes);
    }

    /** ffprobe reports frame rate as a rational like {@code "30000/1001"}. */
    static BigDecimal parseFrameRate(String rational) {
        int slash = rational.indexOf('/');
        if (slash < 0) {
            return parseDecimal(rational);
        }
        BigDecimal numerator = parseDecimal(rational.substring(0, slash));
        BigDecimal denominator = parseDecimal(rational.substring(slash + 1));
        if (denominator.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 3, RoundingMode.HALF_UP);
    }

    private static BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO; // e.g. "N/A"
        }
    }
}
