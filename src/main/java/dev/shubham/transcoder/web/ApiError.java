package dev.shubham.transcoder.web;

import java.time.Instant;

/**
 * Consistent JSON error body for the API, produced by {@link ApiExceptionHandler}. Deliberately
 * small — no stack traces or internals leak to clients.
 *
 * @param timestamp when the error was produced (UTC)
 * @param status    HTTP status code
 * @param error     HTTP reason phrase (e.g. {@code Not Found})
 * @param message   human-readable detail (safe to expose)
 * @param path      request path that produced the error
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
