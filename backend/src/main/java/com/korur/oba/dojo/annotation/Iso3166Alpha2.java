package com.korur.oba.dojo.annotation;

import com.korur.oba.dojo.validator.Iso3166Alpha2Validator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to validate that a field or parameter is a valid ISO 3166-1 Alpha-2 country code.
 * This annotation can be applied to fields or method parameters to enforce the constraint.
 * <p>
 * The validation is handled by the {@link Iso3166Alpha2Validator} class, which checks if the
 * annotated element's value is a valid two-letter country code as per the ISO 3166-1 standard.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @Iso3166Alpha2
 * private String countryCode;
 * }
 * </pre>
 * </p>
 * <p>
 * If the value is not a valid ISO 3166-1 Alpha-2 code, a validation error with the specified
 * message will be triggered.
 * </p>
 * <p>
 * This annotation supports the standard Bean Validation API.
 * </p>
 *
 * @see Iso3166Alpha2Validator
 */
@Constraint(validatedBy = Iso3166Alpha2Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Iso3166Alpha2 {
    String message() default "Invalid ISO 3166-1 Alpha-2 country code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}