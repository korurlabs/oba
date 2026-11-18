package com.korur.oba.apps.kazan.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;

/**
 * The JSON part "message" of a send. The files travel as separate multipart
 * parts, and text may be empty only when at least one file is attached or
 * the message carries a daily update or a pull request share.
 */
@Schema(description = "Chat message create request")
public record ChatMessageCreateRequest(

        // Markdown text with mention and ticket tokens; null or blank only with files or a daily update.
        @Schema(description = "Markdown text with <@userId>, <@everyone>, <@here> and <#ticketId> tokens",
                nullable = true, example = "Release is out, thanks <@3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f>!")
        @Size(max = ChatConstant.MAX_CONTENT_LENGTH, message = "A message can hold at most 2000 characters")
        String content,

        @Schema(description = "Channel to post in; a thread must belong to it",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "3b4c5d6e-7f80-4a91-b2c3-d4e5f6a7b8c9")
        @NotNull(message = "Channel ID is required")
        UUID channelId,

        // Null posts to the channel itself.
        @Schema(description = "Thread to post in; null posts to the channel", nullable = true,
                example = "6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d")
        UUID threadId,

        @Schema(description = "Message this one replies to, in the same channel or thread", nullable = true,
                example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
        UUID replyToMessageId,

        // Set only for the entry of the caller's own daily update in the daily updates channel.
        @Schema(description = "The caller's daily update this message stands for, posted once to the daily "
                + "updates channel; null for every other message", nullable = true,
                example = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f")
        UUID dailyUpdateId,

        // Set only for the entry of the caller's own pull request share in the pull requests channel.
        @Schema(description = "The caller's pull request share this message stands for, posted once to the "
                + "pull requests channel; null for every other message", nullable = true,
                example = "4d5e6f70-8192-4a3b-9c4d-5e6f7a8b9c0d")
        UUID pullRequestShareId,

        // Echoed in the response and the signal, and stored on the message so a resend of the same
        // send returns the stored message instead of posting it twice.
        @Schema(description = "Client-generated key that the response and the signal echo, so the sender's "
                + "optimistic row is replaced instead of duplicated", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "c0a8f2b4-7d1e-4f3a-9b6c-1e2d3f4a5b6c")
        @NotBlank(message = "Client nonce is required")
        @Size(max = ChatConstant.MAX_CLIENT_NONCE_LENGTH, message = "Client nonce must be at most 64 characters")
        String clientNonce
) {
}
