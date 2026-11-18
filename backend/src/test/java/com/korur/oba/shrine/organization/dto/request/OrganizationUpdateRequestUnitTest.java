package com.korur.oba.shrine.organization.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 2. Third-party
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.util.Set;
import java.util.stream.Collectors;

class OrganizationUpdateRequestUnitTest {

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

    private Set<String> violatedProperties(OrganizationUpdateRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void validate_allFieldsProvided_returnsNoViolations() {
        // Arrange
        OrganizationUpdateRequest request = new OrganizationUpdateRequest(
                "Acme", "A description", "Technology", "11-50", "NLD", "https://acme.io");

        // Act
        Set<ConstraintViolation<OrganizationUpdateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validate_nullOptionalFields_returnsNoViolations() {
        // Arrange
        OrganizationUpdateRequest request = new OrganizationUpdateRequest("Acme", null, null, null, null, null);

        // Act
        Set<ConstraintViolation<OrganizationUpdateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validate_nullName_returnsNameViolation() {
        // Arrange
        OrganizationUpdateRequest request = new OrganizationUpdateRequest(null, null, null, null, null, null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertEquals(Set.of("name"), violatedProperties);
    }

    @Test
    void validate_blankName_returnsNameViolation() {
        // Arrange
        OrganizationUpdateRequest emptyName = new OrganizationUpdateRequest("", null, null, null, null, null);
        OrganizationUpdateRequest whitespaceName = new OrganizationUpdateRequest("   ", null, null, null, null, null);

        // Act
        Set<String> emptyViolatedProperties = violatedProperties(emptyName);
        Set<String> whitespaceViolatedProperties = violatedProperties(whitespaceName);

        // Assert
        assertEquals(Set.of("name"), emptyViolatedProperties);
        assertEquals(Set.of("name"), whitespaceViolatedProperties);
    }

    @Test
    void validate_emptyOptionalFields_returnsViolationPerField() {
        // Arrange
        OrganizationUpdateRequest request = new OrganizationUpdateRequest("Acme", "", "", "", "", "");

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertEquals(Set.of("description", "industry", "companySize", "country", "website"), violatedProperties);
    }

    @Test
    void validate_whitespaceOnlyOptionalFields_returnsViolationPerField() {
        // Arrange
        OrganizationUpdateRequest request = new OrganizationUpdateRequest("Acme", " ", " ", " ", " ", " ");

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertEquals(Set.of("description", "industry", "companySize", "country", "website"), violatedProperties);
    }

    @Test
    void validate_fieldsOverMaxLength_returnsViolationPerField() {
        // Arrange
        OrganizationUpdateRequest request = new OrganizationUpdateRequest(
                "a".repeat(256), "a".repeat(513), "a".repeat(101), "a".repeat(51), "a".repeat(4), "a".repeat(513));

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertEquals(
                Set.of("name", "description", "industry", "companySize", "country", "website"),
                violatedProperties);
    }

    @Test
    void validate_fieldsAtMaxLength_returnsNoViolations() {
        // Arrange
        OrganizationUpdateRequest request = new OrganizationUpdateRequest(
                "a".repeat(255), "a".repeat(512), "a".repeat(100), "a".repeat(50), "a".repeat(3), "a".repeat(512));

        // Act
        Set<ConstraintViolation<OrganizationUpdateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }
}
