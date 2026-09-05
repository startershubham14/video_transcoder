package dev.shubham.transcoder.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the global API error advice: domain status exceptions keep their status/reason;
 * unexpected exceptions become a generic 500 with no internals leaked.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private static HttpServletRequest requestFor(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void statusExceptionPreservesStatusAndReason() {
        ResponseEntity<ApiError> response = handler.handleStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job"), requestFor("/jobs/abc"));

        assertEquals(404, response.getStatusCode().value());
        ApiError body = response.getBody();
        assertEquals(404, body.status());
        assertEquals("Not Found", body.error());
        assertEquals("Unknown job", body.message());
        assertEquals("/jobs/abc", body.path());
    }

    @Test
    void unexpectedExceptionBecomesGeneric500() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new RuntimeException("secret internal detail"), requestFor("/uploads"));

        assertEquals(500, response.getStatusCode().value());
        ApiError body = response.getBody();
        assertEquals(500, body.status());
        assertEquals("Internal Server Error", body.error());
        assertEquals("Internal server error", body.message()); // no internals leaked
    }
}
