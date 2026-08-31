package dev.shubham.transcoder.media;

/**
 * Captured result of an external process run.
 *
 * @param exitCode process exit code (always 0 when returned by {@link ProcessRunner#run})
 * @param stdout   captured standard output
 * @param stderr   captured standard error
 */
public record ProcessResult(int exitCode, String stdout, String stderr) {
}
