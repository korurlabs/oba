package com.korur.oba.apps.kaya.lesson.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.lesson.constant.LessonConstant;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;

/**
 * A lesson added from a checkout. The organization and the author come from the machine token.
 */
@Schema(description = "A lesson a machine records")
public record LessonDeliveryRequest(
        @Schema(description = "The checkout's git remote, in any form git accepts; Kaya normalises it",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "git@github.com:korurlabs/village.git")
        @NotBlank(message = LessonConstant.REMOTE_URL_REQUIRED_MESSAGE)
        @Size(max = LessonConstant.REMOTE_URL_MAX_LENGTH, message = LessonConstant.REMOTE_URL_SIZE_MESSAGE)
        String remoteUrl,

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

        @Schema(description = "True applies the lesson to the whole organization; false to the checkout's repository",
                example = "false")
        boolean repoWide,

        @Schema(description = "The Claude Code session that records the lesson; Kaya need not know it yet", nullable = true,
                example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN, message = LessonConstant.CLAUDE_SESSION_ID_INVALID_MESSAGE)
        String claudeSessionId
) {
}
