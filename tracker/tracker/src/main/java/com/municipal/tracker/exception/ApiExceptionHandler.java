package com.municipal.tracker.exception;

import com.municipal.tracker.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), safeMessage(error.getDefaultMessage(), "Invalid value")));
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.putIfAbsent("request", safeMessage(error.getDefaultMessage(), "Invalid request")));
        String message = errors.values().stream().findFirst().orElse("Invalid request");
        return response(HttpStatus.BAD_REQUEST, message, request, errors);
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiErrorResponse> binding(BindException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), safeMessage(error.getDefaultMessage(), "Invalid value")));
        return response(HttpStatus.BAD_REQUEST,
                errors.values().stream().findFirst().orElse("Invalid request"), request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraint(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return response(HttpStatus.BAD_REQUEST,
                errors.values().stream().findFirst().orElse("Invalid request"), request, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> malformed(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MissingServletRequestPartException missingPart
                ? "Required request part is missing: " + missingPart.getRequestPartName()
                : exception instanceof MissingServletRequestParameterException missingParameter
                ? "Required request parameter is missing: " + missingParameter.getParameterName()
                : exception instanceof MethodArgumentTypeMismatchException mismatch
                ? "Invalid value for " + mismatch.getName()
                : unreadableMessage(exception);
        return response(HttpStatus.BAD_REQUEST, message, request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> badRequest(
            IllegalArgumentException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                safeMessage(exception.getMessage(), "Invalid request"), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> conflict(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "The request conflicts with existing data", request, Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(
            ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> routeNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "API endpoint not found", request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Access denied", request, Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> unauthorized(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid email or password", request, Map.of());
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    ResponseEntity<ApiErrorResponse> tooManyLoginAttempts(
            TooManyLoginAttemptsException exception, HttpServletRequest request) {
        return response(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> uploadTooLarge(HttpServletRequest request) {
        return response(HttpStatus.CONTENT_TOO_LARGE,
                "Upload exceeds the configured size limit", request, Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> status(
            ResponseStatusException exception, HttpServletRequest request) {
        return response(HttpStatus.valueOf(exception.getStatusCode().value()),
                exception.getReason(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API exception type={} path={}",
                exception.getClass().getName(), request.getRequestURI());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request, Map.of());
    }

    public static ApiErrorResponse error(
            HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        String safeMessage = safeMessage(message, status.getReasonPhrase());
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                safeMessage, path == null ? "" : path, fieldErrors);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String message, HttpServletRequest request,
            Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(error(
                status, message, request == null ? "" : request.getRequestURI(), fieldErrors));
    }

    private static String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }

    private static String unreadableMessage(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String detail = cause.getMessage();
            if (detail != null && detail.contains("ComplaintSeverity")) {
                return "Unsupported complaint severity";
            }
            if (detail != null && detail.contains("ComplaintIssueType")) {
                return "Unsupported complaint issue type";
            }
        }
        return "Invalid request body";
    }
}
