package com.korur.oba.shrine.access.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Response payload describing a user in a group.
 */
@Schema(description = "Organization group member response")
public record OrganizationGroupMemberResponse(

        @Schema(description = "User id", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID userId,

        @Schema(description = "First and last name; the email when the user has no name",
                example = "Ada Lovelace")
        String fullName,

        @Schema(description = "User email", example = "ada@example.com")
        String email,

        // Null only for a member row of a user who is no longer a member of the organization.
        @Schema(description = "Role of the user in the organization", nullable = true, example = "MEMBER")
        OrganizationMemberRole role
) {
}
