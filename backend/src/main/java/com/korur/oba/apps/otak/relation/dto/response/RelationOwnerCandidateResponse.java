package com.korur.oba.apps.otak.relation.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * An active member of the organization that the relation owner picker offers, with whether the member may
 * open Otak there. A member without Otak is listed so the picker can show why it cannot own a relation.
 */
@Schema(description = "Relation owner candidate response")
public record RelationOwnerCandidateResponse(

        @Schema(description = "User id", example = "4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a")
        UUID userId,

        @Schema(description = "Email address", example = "jane@example.com")
        String email,

        @Schema(description = "First name", nullable = true, example = "Jane")
        String firstName,

        @Schema(description = "Last name", nullable = true, example = "Doe")
        String lastName,

        @Schema(description = "Role in the organization", example = "MEMBER")
        OrganizationMemberRole role,

        @Schema(description = "True when the member may open Otak in the organization; a member without it "
                + "cannot own a relation", example = "true")
        boolean otakAccess
) {
}
