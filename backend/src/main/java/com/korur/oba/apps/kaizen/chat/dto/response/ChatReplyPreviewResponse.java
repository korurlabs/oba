package com.korur.oba.apps.kaizen.chat.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;

/**
 * The quote shown above a reply. When the quoted message is deleted, only its
 * id and the deleted flag are returned, so nothing of the removed text or
 * files remains.
 */
@Schema(description = "Preview of the message a reply quotes")
public record ChatReplyPreviewResponse(

        @Schema(description = "Quoted message id", example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
        UUID messageId,

        @Schema(description = "True when the quoted message was deleted", example = "false")
        boolean deleted,

        @Schema(description = "Author of the quoted message; null when it was deleted", nullable = true,
                example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID authorId,

        @Schema(description = "First 200 characters of the quoted text with tokens rendered; empty when deleted",
                example = "Release is out, thanks @Ada Lovelace!")
        String snippet,

        @Schema(description = "Files of the quoted message; empty when it was deleted or has none")
        List<AttachmentResponse> attachments
) {
}
