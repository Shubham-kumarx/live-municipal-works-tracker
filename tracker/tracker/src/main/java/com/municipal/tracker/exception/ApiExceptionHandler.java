package com.municipal.tracker.exception;

import com.municipal.tracker.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("Invalid request");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            MissingServletRequestPartException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, exception instanceof IllegalArgumentException
                ? exception.getMessage() : "Invalid request");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden() {
        return response(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> unauthorized() {
        return response(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> uploadTooLarge() {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds the configured size limit");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> status(ResponseStatusException exception) {
        return response(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message) {
        String safeMessage = message == null || message.isBlank() ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status).body(new ApiErrorResponse(safeMessage));
    }
}
