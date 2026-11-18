package com.korur.oba.shrine.access.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Response payload describing the apps that an ACTIVE member of an organization can open, from the role,
 * the direct grants and the grants of the member's groups.
 */
@Schema(description = "Member app access response")
public record MemberAppAccessResponse(

        @Schema(description = "User id", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID userId,

        @Schema(description = "First and last name; the email when the user has no name",
                example = "Ada Lovelace")
        String fullName,

        @Schema(description = "Role of the member in the organization", example = "MEMBER")
        OrganizationMemberRole role,

        @Schema(description = "Apps the member can open, in app order", example = "[\"KAZAN\",\"KAYA\"]")
        List<App> apps
) {
}
