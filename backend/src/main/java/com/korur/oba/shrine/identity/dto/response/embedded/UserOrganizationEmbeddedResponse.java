package com.korur.oba.shrine.identity.dto.response.embedded;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;

/**
 * Embedded response representing a user's membership in an organization.
 * Used within UserDetailResponse to list the organizations a user belongs to.
 */
@Schema(description = "User organization membership")
public record UserOrganizationEmbeddedResponse(

        @Schema(description = "Organization id", nullable = false)
        UUID id,

        @Schema(description = "Organization name", nullable = false)
        String name,

        @Schema(description = "User role within the organization", nullable = false)
        OrganizationMemberRole role,

        @Schema(description = "Organization status", nullable = false)
        OrganizationStatus organizationStatus,

        @Schema(description = "Apps the user can open in the organization, in app order; empty when none",
                nullable = false, example = "[\"KAIZEN\",\"KAYA\"]")
        List<App> apps
) {
}
