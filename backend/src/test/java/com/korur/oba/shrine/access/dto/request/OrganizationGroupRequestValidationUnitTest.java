package com.korur.oba.shrine.access.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 2. Third-party
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.util.Set;
import java.util.stream.Collectors;

class OrganizationGroupRequestValidationUnitTest {

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

    private <T> Set<String> violatedProperties(T request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void validate_createNameOnlyWhitespace_returnsNameViolation() {
        // Arrange
        OrganizationGroupCreateRequest request = new OrganizationGroupCreateRequest(" \t ", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertEquals(Set.of("name"), violatedProperties);
    }

    @Test
    void validate_updateNameOnlyWhitespace_returnsNameViolation() {
        // Arrange
        OrganizationGroupUpdateRequest request = new OrganizationGroupUpdateRequest("   ", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertEquals(Set.of("name"), violatedProperties);
    }

    @Test
    void validate_descriptionOnlyWhitespace_returnsDescriptionViolation() {
        // Arrange
        OrganizationGroupCreateRequest request = new OrganizationGroupCreateRequest("Engineering", "  ");

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertEquals(Set.of("description"), violatedProperties);
    }

    @Test
    void validate_nameWithSurroundingWhitespace_returnsNoViolations() {
        // Arrange
        OrganizationGroupCreateRequest request = new OrganizationGroupCreateRequest(" Engineering ", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertTrue(violatedProperties.isEmpty());
    }
}
