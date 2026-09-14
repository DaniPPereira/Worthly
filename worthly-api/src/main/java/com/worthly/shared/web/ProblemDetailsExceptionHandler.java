package com.worthly.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailsExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        return problem(ex.getStatus(), ex.getCode(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", request);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setTitle(status.getReasonPhrase());
        detail.setDetail(code);
        detail.setType(URI.create("https://worthly.local/problems/" + code));
        detail.setProperty("correlationId", correlationId());
        detail.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(detail);
    }

    private static String correlationId() {
        String existing = MDC.get("correlationId");
        return existing == null ? UUID.randomUUID().toString() : existing;
    }
}
