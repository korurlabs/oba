package com.korur.oba.apps.kaizen.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;

/**
 * Request payload used to add a reaction. The service also checks that the
 * emoji is exactly one grapheme.
 */
@Schema(description = "Chat reaction request")
public record ChatReactionRequest(

        @Schema(description = "One emoji grapheme, at most 64 UTF-16 units", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "👍")
        @NotBlank(message = "Emoji is required")
        @Size(max = ChatConstant.MAX_EMOJI_LENGTH, message = "Emoji must be at most 64 characters")
        String emoji
) {
}
