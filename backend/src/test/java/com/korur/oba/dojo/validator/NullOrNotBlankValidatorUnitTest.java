package com.korur.oba.dojo.validator;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NullOrNotBlankValidatorUnitTest {

    private final NullOrNotBlankValidator validator = new NullOrNotBlankValidator();

    @Test
    void isValid_null_returnsTrue() {
        assertTrue(validator.isValid(null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n", " \t\n "})
    void isValid_emptyOrWhitespace_returnsFalse(String value) {
        assertFalse(validator.isValid(value, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", "text with spaces", "0"})
    void isValid_textWithContent_returnsTrue(String value) {
        assertTrue(validator.isValid(value, null));
    }
}
