package com.korur.oba.apps.kaya.plan.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.plan.constant.PlanConstant;
import com.korur.oba.apps.kaya.session.constant.SessionConstant;

@Schema(description = "The feedback a session has received through its hooks")
public record PlanFeedbackAckRequest(
        @Schema(description = "Claude Code session id of the session that received the feedback",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "6f1c2d3e-4a5b-4c6d-8e7f-9a0b1c2d3e4f")
        @NotBlank(message = PlanConstant.ACK_SESSION_ID_REQUIRED_MESSAGE)
        @Pattern(regexp = SessionConstant.SESSION_ID_PATTERN, message = PlanConstant.SESSION_ID_FILTER_MESSAGE)
        String sessionId,

        @Schema(description = "The delivered feedback ids, 1 to 50", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = PlanConstant.ACK_FEEDBACK_IDS_REQUIRED_MESSAGE)
        @Size(min = PlanConstant.ACK_FEEDBACK_IDS_MIN, max = PlanConstant.ACK_FEEDBACK_IDS_MAX,
                message = PlanConstant.ACK_FEEDBACK_IDS_SIZE_MESSAGE)
        List<@NotNull(message = PlanConstant.ACK_FEEDBACK_ID_NULL_MESSAGE) UUID> feedbackIds
) {
}
