package com.brutecx.docflow_backend.api.error;

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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LastManagerViolationException.class)
    public ResponseEntity<ErrorResponse> handleLastManagerViolation(
            LastManagerViolationException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, ErrorCode.LAST_MANAGER_VIOLATION, ex.getMessage(), request);
    }

    @ExceptionHandler(BootstrapActivationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleBootstrapActivationDenied(
            BootstrapActivationDeniedException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.BOOTSTRAP_ACTIVATION_DENIED, ex.getMessage(), request);
    }

    @ExceptionHandler(BootstrapActivationNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleBootstrapActivationNotAllowed(
            BootstrapActivationNotAllowedException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, ErrorCode.BOOTSTRAP_ACTIVATION_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(TenantLifecycleViolationException.class)
    public ResponseEntity<ErrorResponse> handleTenantLifecycleViolation(
            TenantLifecycleViolationException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, ErrorCode.TENANT_LIFECYCLE_VIOLATION, ex.getMessage(), request);
    }

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
        Map<String, String> violations = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            violations.put(String.valueOf(v.getPropertyPath()), v.getMessage());
        }

        Map<String, Object> details = Map.of("violations", violations);

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
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.MALFORMED_JSON,
                "Malformed JSON request body",
                request
        );
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, ErrorCode.USER_ALREADY_EXISTS, ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
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
        return build(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_SERVICE_ERROR, ex.getMessage(), request);
    }

    @ExceptionHandler(SelfActionForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleSelfActionForbidden(
            SelfActionForbiddenException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.SELF_ACTION_FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled exception correlationId={} method={} path={}",
                MDC.get(RequestCorrelationIdFilter.MDC_KEY),
                request.getMethod(),
                request.getRequestURI(),
                ex
        );

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request
        );
    }

    /* ============================= */

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(status)
                .body(
                        ErrorResponse.of(
                                status.value(),
                                status.getReasonPhrase(),
                                errorCode,
                                message,
                                request.getRequestURI()
                        )
                );
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
    ) {
        return ResponseEntity
                .status(status)
                .body(
                        ErrorResponse.of(
                                status.value(),
                                status.getReasonPhrase(),
                                errorCode,
                                message,
                                request.getRequestURI(),
                                details
                        )
                );
    }
}