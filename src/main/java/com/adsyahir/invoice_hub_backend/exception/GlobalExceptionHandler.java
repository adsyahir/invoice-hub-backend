package com.adsyahir.invoice_hub_backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Central error handling. Every response is an RFC 9457 Problem Details document
 * (application/problem+json) with the standard members — type, title, status,
 * detail, instance — plus an "errors" extension for field-level validation.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Base URI for machine-readable problem "type" links. Doesn't need to resolve
    // to a live page, but conventionally documents the error category.
    private static final String TYPE_BASE = "https://api.invoicehub.com/problems/";

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail,
                                  HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + type));
        pd.setTitle(title);
        pd.setInstance(URI.create(request.getRequestURI()));
        return pd;
    }

    // @PreAuthorize denials (method security). Handled here so a genuine 403 isn't
    // masked as a 401 by the /error re-dispatch through the filter chain.
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Access denied",
                "You do not have permission to perform this action", request);
    }

    // Business-rule conflicts (duplicate slug/email, etc.). Carries field errors.
    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex, HttpServletRequest request) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "validation-error", "Validation failed",
                "One or more values conflict with existing data", request);
        pd.setProperty("errors", ex.getErrors());
        return pd;
    }

    // Bean-validation failures on @Valid bodies (@NotBlank, @Email, …). The
    // "errors" extension is a { field: message } map the frontend maps to inputs.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed",
                "One or more fields are invalid", request);
        pd.setProperty("errors", errors);
        return pd;
    }

    // Intentional status exceptions (our orElseThrow 404s/409s/410s). MUST precede
    // the generic Exception handler, or that would turn every 404 into a 500.
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String detail = ex.getReason() != null ? ex.getReason() : "Request failed";
        return problem(status, "request-failed", status.getReasonPhrase(), detail, request);
    }

    // Catch-all. Logs the real cause (stack trace) and returns a safe, generic 500
    // — never leak internals to the client.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "Something went wrong", request);
    }
}
