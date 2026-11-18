package com.korur.oba.dojo.validator;

// 3. Java SDK
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// 2. Third-party
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// 4. Internal
import com.korur.oba.dojo.annotation.Iso3166Alpha2;

/**
 * Validates that a string value is a valid ISO 3166-1 Alpha-2 country code.
 *
 * <p>Null values are considered valid (use {@code @NotNull} for null checks).</p>
 */
public class Iso3166Alpha2Validator implements ConstraintValidator<Iso3166Alpha2, String> {

    private static final Set<String> ISO_COUNTRIES = Arrays.stream(Locale.getISOCountries())
            .collect(Collectors.toSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return ISO_COUNTRIES.contains(value.toUpperCase());
    }
}
