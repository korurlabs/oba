package com.korur.oba.apps.kaizen.chat.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;

/**
 * Search over the live messages of the readable channels of one
 * organization and their threads, or of one channel, newest first, paged by
 * keyset.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChatSearchRequest {

    /**
     * Text matched case-insensitively against the message content.
     */
    @Schema(description = "Text matched case-insensitively against the message content",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "release")
    @NotBlank(message = "Search text is required")
    @Size(min = ChatConstant.MIN_SEARCH_LENGTH, max = ChatConstant.MAX_SEARCH_LENGTH,
            message = "Search text must be 2 to 100 characters")
    private String q;

    /**
     * Keep only messages of this channel and its threads; null searches every
     * readable channel.
     */
    @Schema(description = "Keep only messages of this channel and its threads; null searches every readable channel",
            nullable = true, example = "3b4c5d6e-7f80-4a91-b2c3-d4e5f6a7b8c9")
    private UUID channelId;

    /**
     * Keep only messages of this author.
     */
    @Schema(description = "Keep only messages of this author", nullable = true,
            example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
    private UUID authorId;

    /**
     * Return matches strictly older than this message.
     */
    @Schema(description = "Return matches strictly older than this message", nullable = true,
            example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
    private UUID before;

    /**
     * Page size.
     */
    @Schema(description = "Page size", nullable = true, example = "25")
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = ChatConstant.MAX_PAGE_SIZE, message = "Limit must be at most 50")
    private Integer limit = ChatConstant.DEFAULT_SEARCH_PAGE_SIZE;
}
