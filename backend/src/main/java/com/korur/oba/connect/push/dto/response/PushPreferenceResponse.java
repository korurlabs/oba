package com.korur.oba.connect.push.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal

/**
 * Response payload with the push switches of one user; a user who never saved them gets every switch
 * on.
 */
@Schema(description = "Push preference response")
public record PushPreferenceResponse(

        @Schema(description = "Push for direct mentions, replies, review requests and daily-update nudges",
                example = "true")
        Boolean mentionsAndReplies,

        @Schema(description = "Push for @everyone messages", example = "true")
        Boolean everyone,

        @Schema(description = "Push for @here messages", example = "false")
        Boolean here,

        @Schema(description = "Push for urgent incidents on the user's boards", example = "true")
        Boolean urgentIncidents,

        @Schema(description = "Show the message text in a push", example = "true")
        Boolean showPreviews
) {
}
