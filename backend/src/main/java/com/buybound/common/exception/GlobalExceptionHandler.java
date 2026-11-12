package com.buybound.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> notFound(ResourceNotFoundException ex, HttpServletRequest req) {
    return body(HttpStatus.NOT_FOUND, ex.getMessage(), req);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ApiError> unauthorized(UnauthorizedException ex, HttpServletRequest req) {
    return body(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> conflict(ConflictException ex, HttpServletRequest req) {
    return body(HttpStatus.CONFLICT, ex.getMessage(), req);
  }

  @ExceptionHandler(ExternalServiceException.class)
  public ResponseEntity<ApiError> externalService(ExternalServiceException ex, HttpServletRequest req) {
    log.error("Upstream service error handling {}: {}", req.getRequestURI(), ex.getMessage());
    return body(HttpStatus.BAD_GATEWAY, ex.getMessage(), req);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> badRequest(IllegalArgumentException ex, HttpServletRequest req) {
    return body(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .sorted()
        .reduce((a, b) -> a + "; " + b)
        .orElse("Validation failed");
    String uri = req != null ? req.getRequestURI() : "";
    boolean isAuthEndpoint = uri.endsWith("/api/user/login");
    boolean missingAuthCredential =
        isAuthEndpoint
            && ex.getBindingResult().getFieldErrors().stream()
                .anyMatch(fe ->
                    "password".equals(fe.getField())
                        || ("email".equals(fe.getField()) && "NotBlank".equals(fe.getCode())));
    if (missingAuthCredential) {
      return body(HttpStatus.UNAUTHORIZED, "Email address and password are required.", req);
    }
    return body(HttpStatus.BAD_REQUEST, message, req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
    log.error("Unexpected error handling {}", req.getRequestURI(), ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req);
  }

  private ResponseEntity<ApiError> body(HttpStatus status, String message, HttpServletRequest req) {
    return ResponseEntity.status(status)
        .body(ApiError.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
  }
}
