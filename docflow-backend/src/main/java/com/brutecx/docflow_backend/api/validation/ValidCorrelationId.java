package com.brutecx.docflow_backend.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = CorrelationIdValidator.class)
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCorrelationId {

    String message() default "Invalid correlationId";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}