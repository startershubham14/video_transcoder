package dev.shubham.transcoder.media;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The single place that runs external processes (ffmpeg / ffprobe). Always sets a timeout,
 * drains stdout+stderr concurrently (so the child never blocks on a full pipe), checks the
 * exit code, and destroys a process that overruns. Callers never touch {@link ProcessBuilder}
 * or concatenate argument strings directly.
 */
@Component
public class ProcessRunner {

    /**
     * Run {@code command}, returning its captured output.
     *
     * @throws ProcessExecutionException on non-zero exit, timeout, or I/O failure
     */
    public ProcessResult run(List<String> command, Duration timeout) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new ProcessExecutionException(command, "could not start", e);
        }

        // Drain both streams off-thread to avoid deadlocking on a full OS pipe buffer.
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());

        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ProcessExecutionException(command, "timed out after " + timeout, null);
            }
            int exit = process.exitValue();
            String err = stderr.get();
            if (exit != 0) {
                throw new ProcessExecutionException(command, exit, err);
            }
            return new ProcessResult(exit, stdout.get(), err);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException(command, "interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new ProcessExecutionException(command, "output capture failed", e);
        }
    }

    private static CompletableFuture<String> readAsync(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }
}
