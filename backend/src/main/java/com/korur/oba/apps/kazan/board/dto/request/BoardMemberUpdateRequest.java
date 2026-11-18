package com.korur.oba.apps.kazan.board.dto.request;

import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload used to update a board member's role.
 */
@Schema(description = "Board member update request")
public record BoardMemberUpdateRequest(

        // Updated role for the member.
        @Schema(description = "Member role", nullable = false)
        @NotNull(message = "Role is required")
        BoardMemberRole role
) {
}
