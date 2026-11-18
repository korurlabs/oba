package com.korur.oba.apps.kaizen.ticket.dto.request;

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

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;

class TicketCreateRequestUnitTest {

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

    private TicketCreateRequest request(MissionRank missionRank) {
        return new TicketCreateRequest("Title", null, TicketType.FEATURE, TicketPriority.MEDIUM, TicketStatus.TODO,
                null, missionRank, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void validate_missionRankNull_returnsMissionRankViolation() {
        // Arrange
        TicketCreateRequest request = request(null);

        // Act
        Set<ConstraintViolation<TicketCreateRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<TicketCreateRequest> violation = violations.iterator().next();
        assertEquals("missionRank", violation.getPropertyPath().toString());
        assertEquals("Mission rank is required", violation.getMessage());
    }

    @Test
    void validate_missionRankSet_returnsNoViolations() {
        // Arrange
        TicketCreateRequest request = request(MissionRank.M);

        // Act
        Set<ConstraintViolation<TicketCreateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }
}
