package com.korur.oba.apps.kazan.comment.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal

/**
 * Request payload used to update comment content.
 */
@Schema(description = "Comment update request")
public record CommentUpdateRequest(

        // Updated comment content.
        @Schema(description = "Comment content", nullable = false)
        @NotBlank(message = "Comment content is required")
        @Size(max = 10000, message = "Content must be at most 10000 characters")
        String content
) {
}
