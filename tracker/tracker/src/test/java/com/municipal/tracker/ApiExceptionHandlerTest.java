package com.municipal.tracker.exception;

import com.municipal.tracker.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void mapsNotFoundAndResponseStatusExceptions() {
        assertResponse(handler.notFound(new ResourceNotFoundException("Project", 99), request),
                404, "Project not found: 99");
        assertResponse(handler.status(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "AI unavailable"), request), 503, "AI unavailable");
    }

    @Test
    void mapsUnknownApiRouteToNotFound() {
        assertResponse(handler.routeNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/does-not-exist", "static locations"), request),
                404, "API endpoint not found");
    }

    @Test
    void returnsSafeAuthenticationAndAuthorizationMessages() {
        assertResponse(handler.forbidden(request), 403, "Access denied");
        assertResponse(handler.unauthorized(request), 401, "Invalid email or password");
    }

    @Test
    void mapsBadRequestsAndUploadLimits() {
        assertResponse(handler.badRequest(new IllegalArgumentException("Invalid progress"), request),
                400, "Invalid progress");
        assertResponse(handler.uploadTooLarge(request),
                413, "Upload exceeds the configured size limit");
    }

    @Test
    void unexpectedErrorsDoNotExposeInternalMessage() {
        ResponseEntity<ApiErrorResponse> response = handler.unexpected(
                new IllegalStateException("database-password=secret"), request);

        assertResponse(response, 500, "Unexpected server error");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    @Test
    void staticFactoryCopiesFieldErrorsAndSuppliesFallbacks() {
        ApiErrorResponse response = ApiExceptionHandler.error(
                HttpStatus.BAD_REQUEST, " ", null, java.util.Map.of("progress", "must be at most 100"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Bad Request");
        assertThat(response.path()).isEmpty();
        assertThat(response.fieldErrors()).containsEntry("progress", "must be at most 100");
        assertThat(response.timestamp()).isNotNull();
    }

    private void assertResponse(ResponseEntity<ApiErrorResponse> response, int status, String message) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(status);
        assertThat(response.getBody().message()).isEqualTo(message);
        assertThat(response.getBody().path()).isEqualTo("/api/test");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }
}
