package dev.shubham.transcoder.prepare;

import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * ClamAV adapter for {@link VirusScanner}. Streams the source to the ClamAV daemon
 * (INSTREAM) before splitting. A positive detection fails the job ({@code infected});
 * nothing infected is ever split or encoded.
 */
@Service
public class ClamAvVirusScanner implements VirusScanner {

    /** @return true if the file is clean, false if malware was detected. */
    @Override
    public boolean isClean(Path file) {
        // TODO open socket to clamd host:port, INSTREAM the bytes, parse the verdict.
        throw new UnsupportedOperationException("not implemented");
    }
}
