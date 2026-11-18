package com.korur.oba.apps.kaizen.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * The reader's unread count in one conversation: a channel or a thread.
 * channelId equals id for a channel and is the thread's channel for a
 * thread, so the client can group thread notifications per channel.
 */
@Schema(description = "Unread chat count of one channel or thread")
public record ChatConversationUnreadResponse(

        @Schema(description = "Channel or thread id", example = "6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d")
        UUID id,

        @Schema(description = "The channel itself, or the channel of the thread",
                example = "3b4c5d6e-7f80-4a91-b2c3-d4e5f6a7b8c9")
        UUID channelId,

        @Schema(description = "Unread messages, at most 100", example = "2")
        int unread,

        @Schema(description = "Unread messages that notify the reader, at most 100: a mention of the reader "
                + "(directly, through everyone, or through here while online) or a reply to a live message of the "
                + "reader", example = "1")
        int mentionCount,

        @Schema(description = "Newest message the reader has read; null before the first read", nullable = true,
                example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
        UUID lastReadMessageId,

        @Schema(description = "Creation time of that message, the reader's position (UTC); null before the first "
                + "read", nullable = true, example = "2026-10-20T10:42:00Z")
        Instant lastReadAt
) {
}
