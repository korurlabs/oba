package com.korur.oba.apps.kaizen.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.chat.enums.ChatThreadStatus;

/**
 * Request payload used to close, reopen, archive or unarchive a thread. It is
 * separate from the rename request, so a rename sent from a stale panel never
 * undoes a status change made meanwhile, and the reverse.
 */
@Schema(description = "Chat thread status update request")
public record ChatThreadStatusUpdateRequest(

        @Schema(description = "New status: ACTIVE reopens or unarchives, CLOSED makes the thread read-only, "
                + "ARCHIVED puts it away", requiredMode = Schema.RequiredMode.REQUIRED, example = "CLOSED")
        @NotNull(message = "Status is required")
        ChatThreadStatus status
) {
}
