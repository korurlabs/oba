package com.korur.oba.apps.kazan.sprint.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class SprintCreateRequestValidationUnitTest {

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

    private static SprintCreateRequest request(List<UUID> childBoardIds) {
        return new SprintCreateRequest("Sprint", null, null, null, null, childBoardIds, null);
    }

    private static List<UUID> randomIds(int count) {
        return IntStream.range(0, count).mapToObj(index -> UUID.randomUUID()).collect(Collectors.toList());
    }

    private static Set<String> violatedProperties(SprintCreateRequest request) {
        return validator.validate(request).stream()
                .map((ConstraintViolation<SprintCreateRequest> violation) -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void nullChildBoardIds_hasNoViolation() {
        assertThat(validator.validate(request(null))).isEmpty();
    }

    @Test
    void emptyChildBoardIds_hasNoViolation() {
        assertThat(validator.validate(request(List.of()))).isEmpty();
    }

    @Test
    void fiftyChildBoardIds_hasNoViolation() {
        assertThat(validator.validate(request(randomIds(50)))).isEmpty();
    }

    @Test
    void fiftyOneChildBoardIds_violatesSize() {
        assertThat(violatedProperties(request(randomIds(51)))).containsExactly("childBoardIds");
    }

    @Test
    void nullElement_violatesNotNull() {
        List<UUID> ids = new ArrayList<>();
        ids.add(UUID.randomUUID());
        ids.add(null);

        assertThat(violatedProperties(request(ids))).hasSize(1)
                .allMatch((String property) -> property.startsWith("childBoardIds"));
    }
}
