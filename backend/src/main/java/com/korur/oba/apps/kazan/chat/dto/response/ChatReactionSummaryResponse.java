package com.korur.oba.apps.kazan.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal

/**
 * One emoji on a message with its count, whether the reader reacted with it,
 * and the first users who did.
 */
@Schema(description = "Reaction summary of one emoji on a chat message")
public record ChatReactionSummaryResponse(

        @Schema(description = "Emoji", example = "👍")
        String emoji,

        @Schema(description = "Number of users who reacted with this emoji", example = "3")
        int count,

        @Schema(description = "True when the reader reacted with this emoji", example = "true")
        boolean reactedByMe,

        @Schema(description = "First 10 users who reacted, in reaction order")
        List<UUID> userIds
) {
}
