package dev.shubham.transcoder.prepare;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure-logic test of ffprobe-JSON parsing (no process run).
 */
class FfprobeMediaProbeTest {

    private static final String JSON = """
            {"streams":[
              {"codec_type":"audio","codec_name":"aac"},
              {"codec_type":"video","codec_name":"h264","width":1920,"height":1080,"avg_frame_rate":"30000/1001"}
            ],
            "format":{"duration":"154.200000","size":"734003200"}}
            """;

    @Test
    void parsesVideoStreamAndFormat() {
        ProbeResult probe = FfprobeMediaProbe.parse(JSON, 734003200L);
        assertEquals(1920, probe.width());
        assertEquals(1080, probe.height());
        assertEquals("h264", probe.codec());
        assertEquals(0, probe.durationSeconds().compareTo(new BigDecimal("154.2")));
        assertEquals(734003200L, probe.sizeBytes());
        assertEquals(new BigDecimal("29.970"), probe.fps());
    }

    @Test
    void rejectsInputWithoutVideoStream() {
        String audioOnly = "{\"streams\":[{\"codec_type\":\"audio\"}],\"format\":{\"duration\":\"1\"}}";
        assertThrows(PrepareRejectedException.class, () -> FfprobeMediaProbe.parse(audioOnly, 1L));
    }

    @Test
    void frameRateHandlesRationalsAndZeroDenominator() {
        assertEquals(0, FfprobeMediaProbe.parseFrameRate("30/1").compareTo(new BigDecimal("30")));
        assertEquals(0, FfprobeMediaProbe.parseFrameRate("0/0").compareTo(BigDecimal.ZERO));
    }
}
