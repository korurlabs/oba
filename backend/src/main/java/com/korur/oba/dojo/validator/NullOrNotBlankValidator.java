package com.korur.oba.dojo.validator;

// 2. Third-party
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// 4. Internal
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Validates that a string value is either null or not empty/blank.
 *
 * <p>Null values are considered valid. Empty or whitespace-only strings are invalid.</p>
 */
public class NullOrNotBlankValidator implements ConstraintValidator<NullOrNotBlank, String> {

    @Override
    public void initialize(NullOrNotBlank parameters) {
        // No initialization needed.
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        return value == null || !value.trim().isEmpty();
    }
}
