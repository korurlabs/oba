package com.korur.oba.apps.kaizen.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal

/**
 * The reader's unread counts in one organization: every channel the reader
 * can read and every active thread (neither closed nor archived) the reader
 * is a member of. Each count stops at 100, because the client shows "99+"
 * above 99. Beside each count stands the number of those messages that
 * notify the reader (mentions and replies to the reader's live messages).
 */
@Schema(description = "Unread chat counts of the reader")
public record ChatUnreadResponse(

        @Schema(description = "One entry per readable channel, with its read position")
        List<ChatConversationUnreadResponse> channels,

        @Schema(description = "Threads with unread messages")
        List<ChatConversationUnreadResponse> threads,

        @Schema(description = "Channel and thread counts added up; may exceed 100", example = "6")
        int total,

        @Schema(description = "Channel and thread notification counts added up; may exceed 100", example = "2")
        int mentionTotal
) {
}
