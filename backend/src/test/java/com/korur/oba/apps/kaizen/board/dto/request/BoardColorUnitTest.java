package com.korur.oba.apps.kaizen.board.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 2. Third-party
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.util.Set;
import java.util.stream.Collectors;

// 4. Internal

class BoardColorUnitTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    private Set<String> violatedProperties(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"#3B82F6", "#3b82f6", "#000000"})
    void validate_hexColor_returnsNoViolations(String color) {
        // Arrange
        BoardCreateRequest createRequest = new BoardCreateRequest("Platform", null, "PLAT", color, null, null, null);
        BoardUpdateRequest updateRequest = new BoardUpdateRequest(null, null, null, color, null, null, null);

        // Act
        Set<String> createViolations = violatedProperties(createRequest);
        Set<String> updateViolations = violatedProperties(updateRequest);

        // Assert
        assertTrue(createViolations.isEmpty());
        assertTrue(updateViolations.isEmpty());
    }

    @Test
    void validate_nullColor_returnsNoViolations() {
        // Arrange
        String color = null;
        BoardCreateRequest createRequest = new BoardCreateRequest("Platform", null, "PLAT", color, null, null, null);
        BoardUpdateRequest updateRequest = new BoardUpdateRequest(null, null, null, color, null, null, null);

        // Act
        Set<String> createViolations = violatedProperties(createRequest);
        Set<String> updateViolations = violatedProperties(updateRequest);

        // Assert
        assertTrue(createViolations.isEmpty());
        assertTrue(updateViolations.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"blue", "3B82F6", "#3B82F", "#3B82F6A", "#GGGGGG", "", "#3B82F6 "})
    void validate_nonHexColor_returnsColorViolation(String color) {
        // Arrange
        BoardCreateRequest createRequest = new BoardCreateRequest("Platform", null, "PLAT", color, null, null, null);
        BoardUpdateRequest updateRequest = new BoardUpdateRequest(null, null, null, color, null, null, null);

        // Act
        Set<String> createViolations = violatedProperties(createRequest);
        Set<String> updateViolations = violatedProperties(updateRequest);

        // Assert
        assertEquals(Set.of("color"), createViolations);
        assertEquals(Set.of("color"), updateViolations);
    }
}
