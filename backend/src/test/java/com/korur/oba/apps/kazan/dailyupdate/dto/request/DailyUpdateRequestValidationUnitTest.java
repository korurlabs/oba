package com.korur.oba.apps.kazan.dailyupdate.dto.request;

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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class DailyUpdateRequestValidationUnitTest {

    private static final List<DailyUpdateTicketRequest> LINES =
            List.of(new DailyUpdateTicketRequest(UUID.randomUUID(), 40, null));

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
    void create_nullFocus_isRefused() {
        // Arrange
        DailyUpdateCreateRequest request = new DailyUpdateCreateRequest(LINES, null, null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void create_emptyFocus_isRefused() {
        // Arrange
        DailyUpdateCreateRequest request = new DailyUpdateCreateRequest(LINES, "", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void create_whitespaceOnlyFocus_isRefused() {
        // Arrange
        DailyUpdateCreateRequest request = new DailyUpdateCreateRequest(LINES, " \n ", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void create_focusWithSurroundingWhitespace_isValid() {
        // Arrange
        DailyUpdateCreateRequest request = new DailyUpdateCreateRequest(LINES, " Ship it ", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }

    @Test
    void create_nullQuestions_isValid() {
        // Arrange
        DailyUpdateCreateRequest request = new DailyUpdateCreateRequest(LINES, "Ship it", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }

    @Test
    void create_focusOf2001Characters_isRefused() {
        // Arrange
        DailyUpdateCreateRequest request = new DailyUpdateCreateRequest(LINES, "x".repeat(2001), null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void create_focusOf2000Characters_isValid() {
        // Arrange
        DailyUpdateCreateRequest request = new DailyUpdateCreateRequest(LINES, "x".repeat(2000), null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }

    @Test
    void update_nullFocus_isRefused() {
        // Arrange
        DailyUpdateUpdateRequest request = new DailyUpdateUpdateRequest(LINES, null, null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void update_emptyFocus_isRefused() {
        // Arrange
        DailyUpdateUpdateRequest request = new DailyUpdateUpdateRequest(LINES, "", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void update_whitespaceOnlyFocus_isRefused() {
        // Arrange
        DailyUpdateUpdateRequest request = new DailyUpdateUpdateRequest(LINES, " \n ", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void update_focusWithSurroundingWhitespace_isValid() {
        // Arrange
        DailyUpdateUpdateRequest request = new DailyUpdateUpdateRequest(LINES, " Ship it ", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }

    @Test
    void update_focusOf2001Characters_isRefused() {
        // Arrange
        DailyUpdateUpdateRequest request = new DailyUpdateUpdateRequest(LINES, "x".repeat(2001), null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void preview_nullFocus_isValid() {
        // Arrange
        DailyUpdatePreviewRequest request = new DailyUpdatePreviewRequest(LINES, null, null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }

    @Test
    void preview_whitespaceOnlyFocus_isValid() {
        // Arrange
        DailyUpdatePreviewRequest request = new DailyUpdatePreviewRequest(LINES, "  ", null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).isEmpty();
    }

    @Test
    void preview_focusOf2001Characters_isRefused() {
        // Arrange
        DailyUpdatePreviewRequest request = new DailyUpdatePreviewRequest(LINES, "x".repeat(2001), null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("focus");
    }

    @Test
    void preview_noTickets_isRefused() {
        // Arrange
        DailyUpdatePreviewRequest request = new DailyUpdatePreviewRequest(List.of(), null, null);

        // Act
        Set<String> violatedProperties = violatedProperties(request);

        // Assert
        assertThat(violatedProperties).containsExactly("tickets");
    }
}
