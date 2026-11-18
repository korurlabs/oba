package com.korur.oba.apps.kaizen.board.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * An active member of the board's organization that the add-member picker offers, with whether the member
 * may open Kaizen there. A member without Kaizen is listed so the picker can show why it cannot be added.
 */
@Schema(description = "Board member candidate response")
public record BoardMemberCandidateResponse(

        @Schema(description = "User id", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID userId,

        @Schema(description = "Email address", example = "ada@example.com")
        String email,

        @Schema(description = "First name", nullable = true, example = "Ada")
        String firstName,

        @Schema(description = "Last name", nullable = true, example = "Lovelace")
        String lastName,

        @Schema(description = "Role in the organization", example = "MEMBER")
        OrganizationMemberRole role,

        @Schema(description = "True when the member may open Kaizen in the board's organization; a member "
                + "without it cannot be added to the board", example = "true")
        boolean kaizenAccess
) {
}
