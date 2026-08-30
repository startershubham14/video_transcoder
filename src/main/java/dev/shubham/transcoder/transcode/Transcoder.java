package dev.shubham.transcoder.transcode;

import java.nio.file.Path;

/**
 * Port for encoding a single segment to a rung. Keeps transcode-stage code off the
 * FFmpeg binary directly. The concrete adapter is {@link SegmentTranscoder}.
 */
public interface Transcoder {

    /** @return the encoded output file. */
    Path encode(Path segmentFile, Rung rung);
}
