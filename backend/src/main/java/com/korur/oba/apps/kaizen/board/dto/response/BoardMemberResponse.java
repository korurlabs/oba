package com.korur.oba.apps.kaizen.board.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;

/**
 * Response payload describing a board member.
 */
@Schema(description = "Board member response")
public record BoardMemberResponse(

        // Unique membership identifier.
        @Schema(description = "Board member id", nullable = false)
        UUID id,

        // User identifier for the member.
        @Schema(description = "User id", nullable = false)
        UUID userId,

        // Member email address.
        @Schema(description = "User email", nullable = false)
        String email,

        // Member first name.
        @Schema(description = "User first name", nullable = false)
        String firstName,

        // Member last name.
        @Schema(description = "User last name", nullable = false)
        String lastName,

        // Role assigned within the board.
        @Schema(description = "Member role", nullable = false)
        BoardMemberRole role,

        // Membership creation timestamp.
        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        // Whether the member may open Kaizen in the board's organization now; a member without it keeps
        // the row but cannot be assigned, mentioned or given a review.
        @Schema(description = "True when the member may open Kaizen in the board's organization", example = "true")
        boolean kaizenAccess
) {
}
