package com.korur.oba.apps.kaizen.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;

/**
 * Request payload used to edit a message. The text replaces the old one, and
 * mentions and ticket references are derived from it again.
 */
@Schema(description = "Chat message edit request")
public record ChatMessageUpdateRequest(

        @Schema(description = "New markdown text with mention and ticket tokens", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Release is out, thanks <@everyone>!")
        @NotNull(message = "Content is required")
        @Size(max = ChatConstant.MAX_CONTENT_LENGTH, message = "A message can hold at most 2000 characters")
        String content
) {
}
