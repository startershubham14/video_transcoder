package dev.shubham.transcoder.media;

import java.util.List;

/**
 * Thrown when an external process (ffmpeg/ffprobe) exits non-zero or times out. Carries the
 * command and captured stderr for diagnostics.
 */
public class ProcessExecutionException extends RuntimeException {

    public ProcessExecutionException(List<String> command, int exitCode, String stderr) {
        super("command " + command + " exited " + exitCode + ": " + stderr);
    }

    public ProcessExecutionException(List<String> command, String message, Throwable cause) {
        super("command " + command + " failed: " + message, cause);
    }
}
