package dev.shubham.transcoder.prepare;

import java.nio.file.Path;

/**
 * Port for malware scanning. The concrete adapter is {@link ClamAvScanner}.
 */
public interface VirusScanner {

    /** @return true if the file is clean, false if malware was detected. */
    boolean isClean(Path file);
}
