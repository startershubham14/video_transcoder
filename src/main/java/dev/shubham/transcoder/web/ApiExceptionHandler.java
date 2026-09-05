package dev.shubham.transcoder.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Turns exceptions thrown by controllers into a consistent {@link ApiError} JSON body. Domain
 * code raises {@link ResponseStatusException} with the right status (e.g. 404 from
 * {@code JobStatusService}, 409/413 from {@code UploadHandler}); anything unexpected becomes a
 * generic 500 with no internals leaked.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Domain-raised status exceptions: preserve the status + reason. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        HttpStatus resolved = HttpStatus.resolve(status);
        String reasonPhrase = resolved != null ? resolved.getReasonPhrase() : "";
        String message = ex.getReason() != null ? ex.getReason() : reasonPhrase;
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status, reasonPhrase, message, request.getRequestURI()));
    }

    /** Anything unexpected: 500 with a generic message (never leak the stack / internals). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {}", request.getRequestURI(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                        "Internal server error", request.getRequestURI()));
    }
}
