package com.korur.oba.apps.kaya.ingest.dto.request;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// 2. Third-party
import jakarta.validation.Validation;
import jakarta.validation.Validator;

// 3. Java SDK
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaya.plan.enums.PlanKind;
import com.korur.oba.apps.kaya.plan.enums.PlanVersionSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The content limits of handoff and plan snapshots, which the API answers as 422 GEN_VAL_001. Text
 * is held to 200000 characters; a binary file, whose content holds U+0000, passes at any length,
 * because it is left out before anything is stored.
 */
class IngestSnapshotRequestValidationUnitTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-10-20T09:15:00Z");
    private static final int CONTENT_MAX_LENGTH = 200_000;
    // An image as an older recorder sends it: cut at 200000 code points, of which the characters
    // outside the Basic Multilingual Plane count twice in Java, so it is longer than the limit.
    private static final String LONG_IMAGE_CONTENT = "�PNG\r\n\u001A\n\u0000\u0000\u0000\rIHDR"
            + "😀".repeat(100) + "x".repeat(CONTENT_MAX_LENGTH);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Nested
    class Handoff {

        private IngestHandoffRequest handoff(String content) {
            return new IngestHandoffRequest("export/backend.md", content, CAPTURED_AT);
        }

        @Test
        void textAtTheLimit_isValid() {
            assertEquals(Set.of(), invalidFields(handoff("x".repeat(CONTENT_MAX_LENGTH))));
        }

        @Test
        void textOverTheLimit_isInvalid() {
            assertEquals(Set.of("contentWithinLimit"), invalidFields(handoff("x".repeat(CONTENT_MAX_LENGTH + 1))));
        }

        @Test
        void binaryContentOverTheLimit_isValid() {
            assertEquals(Set.of(), invalidFields(handoff(LONG_IMAGE_CONTENT)));
        }

        @Test
        void emptyContent_isInvalid() {
            assertEquals(Set.of("content"), invalidFields(handoff("")));
        }
    }

    @Nested
    class Plan {

        private IngestPlanRequest plan(String content) {
            return new IngestPlanRequest(PlanKind.SPEC, PlanVersionSource.FILE, "docs/plans/export.md", content,
                    CAPTURED_AT);
        }

        @Test
        void textAtTheLimit_isValid() {
            assertEquals(Set.of(), invalidFields(plan("x".repeat(CONTENT_MAX_LENGTH))));
        }

        @Test
        void textOverTheLimit_isInvalid() {
            assertEquals(Set.of("contentWithinLimit"), invalidFields(plan("x".repeat(CONTENT_MAX_LENGTH + 1))));
        }

        @Test
        void binaryContentOverTheLimit_isValid() {
            assertEquals(Set.of(), invalidFields(plan(LONG_IMAGE_CONTENT)));
        }

        @Test
        void emptyContent_isInvalid() {
            assertEquals(Set.of("content"), invalidFields(plan("")));
        }
    }
}
