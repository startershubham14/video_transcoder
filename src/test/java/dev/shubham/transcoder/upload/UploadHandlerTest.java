package dev.shubham.transcoder.upload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic test of multipart part-count derivation (no I/O).
 */
class UploadHandlerTest {

    private static final long MIB_16 = 16L * 1024 * 1024;

    @Test
    void roundsUpToCoverTheSize() {
        assertEquals(1, UploadHandler.partCount(1, MIB_16));
        assertEquals(1, UploadHandler.partCount(MIB_16, MIB_16));
        assertEquals(2, UploadHandler.partCount(MIB_16 + 1, MIB_16));
        assertEquals(3, UploadHandler.partCount(2 * MIB_16 + 1, MIB_16));
    }

    @Test
    void neverBelowOne() {
        assertEquals(1, UploadHandler.partCount(0, MIB_16));
    }

    @Test
    void clampsToS3MaxParts() {
        // 200 GiB at 16 MiB/part would be 12800 parts → clamp to 10000
        assertEquals(10_000, UploadHandler.partCount(200L * 1024 * 1024 * 1024, MIB_16));
    }
}
