package com.korur.oba.apps.kaizen.board.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK
import java.util.UUID;

import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;

/**
 * Request payload used to add a user as a board member.
 */
@Schema(description = "Board member add request")
public record BoardMemberAddRequest(

        // Target user identifier to add as a member.
        @Schema(description = "User id", nullable = false)
        @NotNull(message = "User id is required")
        UUID userId,

        // Assigned role for the new member.
        @Schema(description = "Member role", nullable = false)
        @NotNull(message = "Role is required")
        BoardMemberRole role
) {
}
