package com.korur.oba.connect.push.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.push.constant.PushConstant;

/**
 * Request payload that replaces all of the caller's push switches; every switch is required, so a
 * missing one is refused instead of silently turning a kind of push off.
 */
@Schema(description = "Push preference update request")
public record PushPreferenceUpdateRequest(

        @Schema(description = "Push for direct mentions, replies, review requests and daily-update nudges",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        @NotNull(message = PushConstant.MSG_MENTIONS_AND_REPLIES_REQUIRED)
        Boolean mentionsAndReplies,

        @Schema(description = "Push for @everyone messages", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "true")
        @NotNull(message = PushConstant.MSG_EVERYONE_REQUIRED)
        Boolean everyone,

        @Schema(description = "Push for @here messages", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "false")
        @NotNull(message = PushConstant.MSG_HERE_REQUIRED)
        Boolean here,

        @Schema(description = "Push for urgent incidents on the user's boards",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        @NotNull(message = PushConstant.MSG_URGENT_INCIDENTS_REQUIRED)
        Boolean urgentIncidents,

        @Schema(description = "Show the message text in a push; off names only the conversation",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        @NotNull(message = PushConstant.MSG_SHOW_PREVIEWS_REQUIRED)
        Boolean showPreviews
) {
}
