package com.korur.oba.apps.kazan.board.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.enums.AgentFlow;
import com.korur.oba.shrine.identity.enums.AgentProvider;
import com.korur.oba.shrine.identity.enums.AgentRole;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * An active member of the board's organization that the add-member picker offers, with whether the member
 * may open Kazan there. A member without Kazan is listed so the picker can show why it cannot be added.
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

        @Schema(description = "True when the member may open Kazan in the board's organization; a member "
                + "without it cannot be added to the board", example = "true")
        boolean kazanAccess,

        @Schema(description = "User kind; the picker badges an agent", nullable = false, example = "HUMAN")
        UserKind kind,

        @Schema(description = "Agent role; null for a person", nullable = true)
        AgentRole agentRole,

        @Schema(description = "Agent flow; null for a person", nullable = true)
        AgentFlow agentFlow,

        @Schema(description = "Agent provider; null for a person", nullable = true)
        AgentProvider agentProvider
) {
}
