package dev.shubham.transcoder.prepare;

import java.nio.file.Path;

/**
 * Port for media inspection. Keeps prepare-stage code off the ffprobe binary directly.
 * The concrete adapter is {@link FfprobeMediaProbe}.
 */
public interface MediaProbe {

    /** @throws RuntimeException (permanent) if the input is invalid/corrupt. */
    ProbeResult probe(Path sourceFile);
}
