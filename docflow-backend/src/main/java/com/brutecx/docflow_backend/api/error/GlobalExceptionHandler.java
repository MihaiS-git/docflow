package com.brutecx.docflow_backend.api.error;

import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.MissingActiveAuditSigningKeyException;
import com.brutecx.docflow_backend.domain.tenant.TenantLifecycleViolationException;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String SCHEMA_VERSION = "docflow_siem_v1";

    /* ============================= */
    /*  Domain Exceptions            */
    /* ============================= */

    @ExceptionHandler(MissingActiveAuditSigningKeyException.class)
    public ResponseEntity<ErrorResponse> handleMissingAuditSigningKey(
            MissingActiveAuditSigningKeyException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.FAILED_DEPENDENCY,
                ErrorCode.AUDIT_EXPORT_SIGNING_KEY_MISSING,
                ex,
                request,
                null);

        return build(HttpStatus.FAILED_DEPENDENCY,
                ErrorCode.AUDIT_EXPORT_SIGNING_KEY_MISSING,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(LifecycleAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleLifecycleAccessDenied(
            LifecycleAccessDeniedException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.FORBIDDEN,
                ErrorCode.ACCOUNT_LOCKED,
                ex,
                request,
                null);

        return build(HttpStatus.FORBIDDEN,
                ErrorCode.ACCOUNT_LOCKED,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(LastManagerViolationException.class)
    public ResponseEntity<ErrorResponse> handleLastManagerViolation(
            LastManagerViolationException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.CONFLICT,
                ErrorCode.LAST_MANAGER_VIOLATION,
                ex,
                request,
                null);

        return build(HttpStatus.CONFLICT,
                ErrorCode.LAST_MANAGER_VIOLATION,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(AuditArchivedRangeVerificationException.class)
    public ResponseEntity<ErrorResponse> handleAuditArchivedRangeVerification(
            AuditArchivedRangeVerificationException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.CONFLICT,
                ErrorCode.AUDIT_VERIFY_INCLUDES_ARCHIVED_DATA,
                ex,
                request,
                null);

        return build(HttpStatus.CONFLICT,
                ErrorCode.AUDIT_VERIFY_INCLUDES_ARCHIVED_DATA,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(BootstrapActivationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleBootstrapActivationDenied(
            BootstrapActivationDeniedException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.FORBIDDEN,
                ErrorCode.BOOTSTRAP_ACTIVATION_DENIED,
                ex,
                request,
                null);

        return build(HttpStatus.FORBIDDEN,
                ErrorCode.BOOTSTRAP_ACTIVATION_DENIED,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(BootstrapActivationNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleBootstrapActivationNotAllowed(
            BootstrapActivationNotAllowedException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.CONFLICT,
                ErrorCode.BOOTSTRAP_ACTIVATION_NOT_ALLOWED,
                ex,
                request,
                null);

        return build(HttpStatus.CONFLICT,
                ErrorCode.BOOTSTRAP_ACTIVATION_NOT_ALLOWED,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(TenantLifecycleViolationException.class)
    public ResponseEntity<ErrorResponse> handleTenantLifecycleViolation(
            TenantLifecycleViolationException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.CONFLICT,
                ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                ex,
                request,
                null);

        return build(HttpStatus.CONFLICT,
                ErrorCode.TENANT_LIFECYCLE_VIOLATION,
                ex.getMessage(),
                request);
    }

    /* ============================= */
    /*  Validation Exceptions        */
    /* ============================= */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        Map<String, Object> details = Map.of("fieldErrors", fieldErrors);

        logHandled(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                ex,
                request,
                details);

        return build(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request,
                details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            violations.put(String.valueOf(v.getPropertyPath()), v.getMessage());
        }

        Map<String, Object> details = Map.of("violations", violations);

        logHandled(HttpStatus.BAD_REQUEST,
                ErrorCode.CONSTRAINT_VIOLATION,
                ex,
                request,
                details);

        return build(HttpStatus.BAD_REQUEST,
                ErrorCode.CONSTRAINT_VIOLATION,
                "Request constraint violation",
                request,
                details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_JSON,
                ex,
                request,
                null);

        return build(HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_JSON,
                "Malformed JSON request body",
                request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                ex,
                request,
                null);

        return build(HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.CONFLICT,
                ErrorCode.USER_ALREADY_EXISTS,
                ex,
                request,
                null);

        return build(HttpStatus.CONFLICT,
                ErrorCode.USER_ALREADY_EXISTS,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.CONFLICT,
                ErrorCode.DATA_INTEGRITY_VIOLATION,
                ex,
                request,
                null);

        return build(HttpStatus.CONFLICT,
                ErrorCode.DATA_INTEGRITY_VIOLATION,
                "Request could not be completed due to a data integrity constraint.",
                request);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClientException(
            RestClientException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.BAD_GATEWAY,
                ErrorCode.UPSTREAM_SERVICE_ERROR,
                ex,
                request,
                null);

        return build(HttpStatus.BAD_GATEWAY,
                ErrorCode.UPSTREAM_SERVICE_ERROR,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(SelfActionForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleSelfActionForbidden(
            SelfActionForbiddenException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.FORBIDDEN,
                ErrorCode.SELF_ACTION_FORBIDDEN,
                ex,
                request,
                null);

        return build(HttpStatus.FORBIDDEN,
                ErrorCode.SELF_ACTION_FORBIDDEN,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        logHandled(HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_ARGUMENT,
                ex,
                request,
                null);

        return build(HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_ARGUMENT,
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {
        logUnhandled(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                ex,
                request);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request);
    }

    /* ============================= */
    /*  Structured Logging Helpers   */
    /* ============================= */

    private void logHandled(
            HttpStatus status,
            ErrorCode errorCode,
            Exception ex,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        boolean includeStacktrace = status.is5xxServerError();

        Actor actor = resolveActor();
        String correlationId = MDC.get(RequestCorrelationIdFilter.MDC_KEY);

        if (includeStacktrace) {
            log.error("application_error",
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
            log.warn("application_error",
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
            HttpServletRequest request
    ) {
        Actor actor = resolveActor();
        String correlationId = MDC.get(RequestCorrelationIdFilter.MDC_KEY);

        log.error("application_error",
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
        Authentication auth = SecurityContextHolder.getContext() != null
                ? SecurityContextHolder.getContext().getAuthentication()
                : null;

        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return new Actor("ANONYMOUS", "anonymous", List.of());
        }

        String name = auth.getName() != null ? auth.getName() : "unknown";

        List<String> roles = new ArrayList<>();
        if (auth.getAuthorities() != null) {
            auth.getAuthorities().forEach(a -> {
                if (a != null && a.getAuthority() != null && !a.getAuthority().isBlank()) {
                    roles.add(a.getAuthority());
                }
            });
        }

        return new Actor("USER", name, roles);
    }

    private record Actor(String type, String name, List<String> roles) {
    }

    /* ============================= */

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(
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
                .body(ErrorResponse.of(
                        status.value(),
                        status.getReasonPhrase(),
                        errorCode,
                        message,
                        request.getRequestURI(),
                        details
                ));
    }
}