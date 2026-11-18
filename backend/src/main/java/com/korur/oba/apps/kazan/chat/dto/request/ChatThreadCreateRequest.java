package com.korur.oba.apps.kazan.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.dojo.annotation.NullOrNotBlank;

/**
 * Request payload used to start a thread on a channel message.
 */
@Schema(description = "Chat thread create request")
public record ChatThreadCreateRequest(

        @Schema(description = "Live channel message the thread starts on", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
        @NotNull(message = "Root message ID is required")
        UUID rootMessageId,

        // Null takes the default title derived from the root message; a
        // given title must not be blank, as on update.
        @Schema(description = "Thread title; null takes the default derived from the root message", nullable = true,
                example = "Release checklist")
        @Size(min = ChatConstant.MIN_THREAD_TITLE_LENGTH, max = ChatConstant.MAX_THREAD_TITLE_LENGTH,
                message = "Title must be 1 to 100 characters")
        @NullOrNotBlank(message = "Title must not be blank")
        String title
) {
}
