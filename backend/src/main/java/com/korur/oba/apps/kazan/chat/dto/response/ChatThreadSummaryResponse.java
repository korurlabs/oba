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
 * The thread line shown under the message a thread starts on.
 */
@Schema(description = "Summary of the thread started on a chat message")
public record ChatThreadSummaryResponse(

        @Schema(description = "Thread id", example = "6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d")
        UUID threadId,

        @Schema(description = "Thread title", example = "Release checklist")
        String title,

        @Schema(description = "Number of live messages in the thread", example = "12")
        int messageCount,

        @Schema(description = "Time of the last reply, or of creation before the first reply (UTC)",
                example = "2026-10-20T10:42:00Z")
        Instant lastMessageAt,

        @Schema(description = "Effective status: ARCHIVED also for an ACTIVE thread inactive longer than its "
                + "auto-archive window", example = "ACTIVE")
        ChatThreadStatus status,

        @Schema(description = "Up to 3 current members: the most recent repliers, then the creator, then the "
                + "others by join time")
        List<UUID> memberPreviewIds,

        @Schema(description = "Number of thread members", example = "5")
        int memberCount
) {
}
