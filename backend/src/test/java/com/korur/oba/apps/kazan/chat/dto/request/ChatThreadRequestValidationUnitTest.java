package com.korur.oba.apps.kazan.chat.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class ChatThreadRequestValidationUnitTest {

    private static final UUID ROOT_MESSAGE_ID = UUID.randomUUID();

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

    @Test
    void create_nullTitle_isValid() {
        // Arrange
        ChatThreadCreateRequest request = new ChatThreadCreateRequest(ROOT_MESSAGE_ID, null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }

    @Test
    void create_titleWithSurroundingWhitespace_isValid() {
        // Arrange
        ChatThreadCreateRequest request = new ChatThreadCreateRequest(ROOT_MESSAGE_ID, "  Release  ");

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }

    @Test
    void create_whitespaceOnlyTitle_isRefused() {
        // Arrange
        ChatThreadCreateRequest request = new ChatThreadCreateRequest(ROOT_MESSAGE_ID, " \t\n ");

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("title");
    }

    @Test
    void create_emptyTitle_isRefused() {
        // Arrange
        ChatThreadCreateRequest request = new ChatThreadCreateRequest(ROOT_MESSAGE_ID, "");

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("title");
    }

    @Test
    void update_whitespaceOnlyTitle_isRefused() {
        // Arrange
        ChatThreadUpdateRequest request = new ChatThreadUpdateRequest("   ", 60);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("title");
    }

    @Test
    void update_titleWithSurroundingWhitespace_isValid() {
        // Arrange
        ChatThreadUpdateRequest request = new ChatThreadUpdateRequest("  Release  ", 60);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }
}
