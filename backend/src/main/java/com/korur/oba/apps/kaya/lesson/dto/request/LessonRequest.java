package com.korur.oba.apps.kaya.lesson.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;

/**
 * The fields a member writes on a lesson. An update replaces all three, so a null repository moves
 * the lesson to the whole organization.
 */
@Schema(description = "A lesson as a member writes it; an update replaces every field")
public record LessonRequest(
        @Schema(description = "The rule as a sentence, 1 to 300 characters", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Run the migration before the entity change")
        @NotBlank(message = LessonConstant.TITLE_REQUIRED_MESSAGE)
        @Size(min = LessonConstant.TITLE_MIN_LENGTH, max = LessonConstant.TITLE_MAX_LENGTH,
                message = LessonConstant.TITLE_SIZE_MESSAGE)
        String title,

        @Schema(description = "What went wrong, why, and the correct approach, as markdown, 1 to 4000 characters",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "The context failed schema validation because the entity landed first.")
        @NotBlank(message = LessonConstant.BODY_REQUIRED_MESSAGE)
        @Size(min = LessonConstant.BODY_MIN_LENGTH, max = LessonConstant.BODY_MAX_LENGTH,
                message = LessonConstant.BODY_SIZE_MESSAGE)
        String body,

        @Schema(description = "The repository the lesson applies to; null applies it to the whole organization",
                nullable = true, example = "3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b")
        UUID repositoryId
) {
}
