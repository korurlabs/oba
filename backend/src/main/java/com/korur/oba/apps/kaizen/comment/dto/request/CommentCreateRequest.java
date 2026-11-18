package com.korur.oba.apps.kaizen.comment.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 3. Java SDK

// 4. Internal

/**
 * Request payload used to create a new comment.
 */
@Schema(description = "Comment create request")
public record CommentCreateRequest(

        // Comment content text.
        @Schema(description = "Comment content", nullable = false)
        @NotBlank(message = "Comment content is required")
        @Size(max = 10000, message = "Content must be at most 10000 characters")
        String content
) {
}
