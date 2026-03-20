package com.brutecx.docflow_backend.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CorrelationIdValidator implements ConstraintValidator<ValidCorrelationId, String> {

    private static final int MAX_LENGTH = 64;

    private static final String PATTERN = "^[a-zA-Z0-9\\-_:]+$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        if (value == null) {
            return true; // handled by @RequestParam(required = false)
        }

        String v = value.trim();

        if (v.isEmpty()) {
            return true; // treat empty as not provided
        }

        if (v.length() > MAX_LENGTH) {
            return false;
        }

        return v.matches(PATTERN);
    }
}