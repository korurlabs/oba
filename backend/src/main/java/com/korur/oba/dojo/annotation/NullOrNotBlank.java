package com.korur.oba.dojo.annotation;

import com.korur.oba.dojo.validator.NullOrNotBlankValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a String field is either null or not blank.
 *
 * Null passes, so the field stays optional; an empty or whitespace-only string fails, so a
 * client that has no value sends null instead of "". The check is done by
 * {@link NullOrNotBlankValidator}.
 *
 * @see NullOrNotBlankValidator
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = NullOrNotBlankValidator.class)
public @interface NullOrNotBlank {
    String message() default "{javax.validation.constraints.NullOrNotBlank.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}