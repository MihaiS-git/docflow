package com.brutecx.docflow_backend.api.error;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.*;

import static net.logstash.logback.argument.StructuredArguments.kv;

@SuppressWarnings({
        "unused",
        "LoggingPlaceholderCountMatchesArgumentCount",
        "ConstantConditions",
        "SameParameterValue"
})
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String SCHEMA_VERSION = "docflow_siem_v1";

    private final AuditRequestContextExtractor contextExtractor;

    /* ============================= */
    /*  Domain Exceptions (generic)  */
    /* ============================= */

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex,
            HttpServletRequest request
    ) {

        AuditRequestContext ctx = contextExtractor.from(request);
        String correlationId = ctx.correlationId();

        logHandled(
                ex.status(),
                ex.errorCode(),
                ex,
                request,
                null,
                correlationId
        );

        return build(
                ex.status(),
                ex.errorCode(),
                ex.getMessage(),
                request
        );
    }

    /* ============================= */
    /*  Validation Exceptions        */
    /* ============================= */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {

        AuditRequestContext ctx = contextExtractor.from(request);
        String correlationId = ctx.correlationId();

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        Map<String, Object> details = Map.of("fieldErrors", fieldErrors);

        logHandled(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                ex,
                request,
                details,
                correlationId
        );

        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request,
                details
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {

        AuditRequestContext ctx = contextExtractor.from(request);
        String correlationId = ctx.correlationId();

        Map<String, String> violations = new LinkedHashMap<>();

        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            violations.put(
                    String.valueOf(v.getPropertyPath()),
                    v.getMessage()
            );
        }

        Map<String, Object> details = Map.of("violations", violations);

        logHandled(
                HttpStatus.BAD_REQUEST,
                ErrorCode.CONSTRAINT_VIOLATION,
                ex,
                request,
                details,
                correlationId
        );

        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.CONSTRAINT_VIOLATION,
                "Request constraint violation",
                request,
                details
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {

        AuditRequestContext ctx = contextExtractor.from(request);
        String correlationId = ctx.correlationId();

        logHandled(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_JSON,
                ex,
                request,
                null,
                correlationId
        );

        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_JSON,
                "Malformed JSON request body",
                request
        );
    }

    /* ============================= */
    /*  Infrastructure Exceptions    */
    /* ============================= */

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {

        AuditRequestContext ctx = contextExtractor.from(request);
        String correlationId = ctx.correlationId();

        logHandled(
                HttpStatus.CONFLICT,
                ErrorCode.DATA_INTEGRITY_VIOLATION,
                ex,
                request,
                null,
                correlationId
        );

        return build(
                HttpStatus.CONFLICT,
                ErrorCode.DATA_INTEGRITY_VIOLATION,
                "Request could not be completed due to a data integrity constraint.",
                request
        );
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClientException(
            RestClientException ex,
            HttpServletRequest request
    ) {

        AuditRequestContext ctx = contextExtractor.from(request);
        String correlationId = ctx.correlationId();

        logHandled(
                HttpStatus.BAD_GATEWAY,
                ErrorCode.UPSTREAM_SERVICE_ERROR,
                ex,
                request,
                null,
                correlationId
        );

        return build(
                HttpStatus.BAD_GATEWAY,
                ErrorCode.UPSTREAM_SERVICE_ERROR,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {

        AuditRequestContext ctx = contextExtractor.from(request);
        String correlationId = ctx.correlationId();

        logHandled(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_ARGUMENT,
                ex,
                request,
                null,
                correlationId
        );

        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_ARGUMENT,
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {

        AuditRequestContext ctx = contextExtractor.from(request);
        String correlationId = ctx.correlationId();

        logUnhandled(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                ex,
                request,
                correlationId
        );

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request
        );
    }

    /* ============================= */
    /*  Structured Logging Helpers   */
    /* ============================= */

    private void logHandled(
            HttpStatus status,
            ErrorCode errorCode,
            Exception ex,
            HttpServletRequest request,
            Map<String, Object> details,
            String correlationId
    ) {

        boolean includeStacktrace = status.is5xxServerError();

        Actor actor = resolveActor();

        if (includeStacktrace) {

            log.error(
                    "application_error",
                    kv("schema_version", SCHEMA_VERSION),
                    kv("event.category", "application"),
                    kv("event.action", "handled_exception"),
                    kv("event.outcome", "failure"),
                    kv("correlation.id", correlationId),
                    kv("http.method", request.getMethod()),
                    kv("http.path", request.getRequestURI()),
                    kv("http.status_code", status.value()),
                    kv("error.code", errorCode.name()),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    kv("actor.type", actor.type()),
                    kv("actor.name", actor.name()),
                    kv("actor.roles", actor.roles()),
                    details != null ? kv("error.details", details) : null,
                    ex
            );

        } else {

            log.warn(
                    "application_error",
                    kv("schema_version", SCHEMA_VERSION),
                    kv("event.category", "application"),
                    kv("event.action", "handled_exception"),
                    kv("event.outcome", "failure"),
                    kv("correlation.id", correlationId),
                    kv("http.method", request.getMethod()),
                    kv("http.path", request.getRequestURI()),
                    kv("http.status_code", status.value()),
                    kv("error.code", errorCode.name()),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    kv("actor.type", actor.type()),
                    kv("actor.name", actor.name()),
                    kv("actor.roles", actor.roles()),
                    details != null ? kv("error.details", details) : null
            );
        }
    }

    private void logUnhandled(
            HttpStatus status,
            ErrorCode errorCode,
            Exception ex,
            HttpServletRequest request,
            String correlationId
    ) {

        Actor actor = resolveActor();

        log.error(
                "application_error",
                kv("schema_version", SCHEMA_VERSION),
                kv("event.category", "application"),
                kv("event.action", "request_failed"),
                kv("event.outcome", "failure"),
                kv("correlation.id", correlationId),
                kv("http.method", request.getMethod()),
                kv("http.path", request.getRequestURI()),
                kv("http.status_code", status.value()),
                kv("error.code", errorCode.name()),
                kv("exception.class", ex.getClass().getSimpleName()),
                kv("actor.type", actor.type()),
                kv("actor.name", actor.name()),
                kv("actor.roles", actor.roles()),
                ex
        );
    }

    private Actor resolveActor() {

        Authentication auth =
                SecurityContextHolder.getContext() != null
                        ? SecurityContextHolder.getContext().getAuthentication()
                        : null;

        if (auth == null
                || auth instanceof AnonymousAuthenticationToken
                || !auth.isAuthenticated()) {

            return new Actor("ANONYMOUS", "anonymous", List.of());
        }

        String name = auth.getName() != null ? auth.getName() : "unknown";

        List<String> roles = new ArrayList<>();

        if (auth.getAuthorities() != null) {

            auth.getAuthorities().forEach(a -> {

                if (a != null
                        && a.getAuthority() != null
                        && !a.getAuthority().isBlank()) {

                    roles.add(a.getAuthority());
                }
            });
        }

        return new Actor("USER", name, roles);
    }

    private record Actor(String type, String name, List<String> roles) {}

    /* ============================= */

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {

        return ResponseEntity.status(status)
                .body(ErrorResponse.create(
                        status.value(),
                        status.getReasonPhrase(),
                        errorCode,
                        message,
                        request.getRequestURI()
                ));
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {

        return ResponseEntity.status(status)
                .body(ErrorResponse.create(
                        status.value(),
                        status.getReasonPhrase(),
                        errorCode,
                        message,
                        request.getRequestURI(),
                        details
                ));
    }
}