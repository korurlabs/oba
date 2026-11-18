package com.korur.oba.apps.kaizen.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;

/**
 * Request payload used to rename a thread and set its auto-archive window.
 * The window is checked against the allowed options in the service, so the
 * client gets the domain error KAI_VAL_050 rather than a generic one.
 */
@Schema(description = "Chat thread update request")
public record ChatThreadUpdateRequest(

        @Schema(description = "Thread title", requiredMode = Schema.RequiredMode.REQUIRED, example = "Release checklist")
        @NotBlank(message = "Title is required")
        @Size(max = ChatConstant.MAX_THREAD_TITLE_LENGTH, message = "Title must be at most 100 characters")
        String title,

        @Schema(description = "Minutes of inactivity after which the thread archives: 60, 1440, 4320 or 10080",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "10080")
        @NotNull(message = "Auto-archive minutes are required")
        Integer autoArchiveMinutes
) {
}
