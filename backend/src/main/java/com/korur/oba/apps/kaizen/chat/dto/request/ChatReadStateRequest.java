package com.korur.oba.apps.kaizen.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Request payload used to mark a channel or one of its threads read up to a message.
 * The position only moves forward; an older message is ignored, so a late
 * request from another tab never makes read messages unread again.
 */
@Schema(description = "Chat read state request")
public record ChatReadStateRequest(

        @Schema(description = "Channel to mark, or the channel of the thread", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "3b4c5d6e-7f80-4a91-b2c3-d4e5f6a7b8c9")
        @NotNull(message = "Channel ID is required")
        UUID channelId,

        // Null marks the channel.
        @Schema(description = "Thread to mark; null marks the channel", nullable = true,
                example = "6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d")
        UUID threadId,

        @Schema(description = "Newest message the reader has seen", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
        @NotNull(message = "Last read message ID is required")
        UUID lastReadMessageId
) {
}
