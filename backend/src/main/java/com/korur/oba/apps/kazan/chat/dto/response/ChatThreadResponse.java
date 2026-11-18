package com.korur.oba.apps.kazan.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.enums.ChatThreadStatus;

/**
 * A thread as one reader sees it, with its root message and its members.
 */
@Schema(description = "Chat thread response")
public record ChatThreadResponse(

        @Schema(description = "Thread id", example = "6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d")
        UUID id,

        @Schema(description = "Channel of the thread", example = "3b4c5d6e-7f80-4a91-b2c3-d4e5f6a7b8c9")
        UUID channelId,

        @Schema(description = "Message the thread starts on", example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
        UUID rootMessageId,

        @Schema(description = "Root message; the deleted placeholder when the root was deleted")
        ChatMessageResponse rootMessage,

        @Schema(description = "Thread title", example = "Release checklist")
        String title,

        @Schema(description = "User who started the thread", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID createdBy,

        @Schema(description = "Number of live messages in the thread", example = "12")
        int messageCount,

        @Schema(description = "Time of the last reply, or of creation before the first reply (UTC)",
                example = "2026-10-20T10:42:00Z")
        Instant lastMessageAt,

        @Schema(description = "Minutes of inactivity after which the thread archives", example = "10080")
        int autoArchiveMinutes,

        @Schema(description = "Effective status: ARCHIVED also for an ACTIVE thread inactive longer than its "
                + "auto-archive window", example = "CLOSED")
        ChatThreadStatus status,

        @Schema(description = "When the effective status took effect (UTC): the last status change, or the end of the "
                + "auto-archive window for a thread archived by inactivity; null when the status was never changed",
                nullable = true, example = "2026-10-21T08:15:00Z")
        Instant statusChangedAt,

        @Schema(description = "User who set the effective status, by hand or with a reply that revived an archived "
                + "thread; null when the status was never changed and for a thread archived by inactivity",
                nullable = true)
        ChatUserResponse statusChangedBy,

        @Schema(description = "True when the reader is a member of the thread", example = "true")
        boolean member,

        @Schema(description = "Thread members")
        List<ChatUserResponse> members,

        @Schema(description = "True when the reader may rename the thread, change its auto-archive window and "
                + "change its status", example = "false")
        boolean canManage
) {
}
