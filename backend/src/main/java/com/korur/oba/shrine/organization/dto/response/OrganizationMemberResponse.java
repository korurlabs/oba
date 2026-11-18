package com.korur.oba.shrine.organization.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

/**
 * Response payload describing an organization member.
 */
@Schema(description = "Organization member response")
public record OrganizationMemberResponse(

        // Unique membership identifier.
        @Schema(description = "Organization member id", nullable = false)
        UUID id,

        // User identifier for the member.
        @Schema(description = "User id", nullable = false)
        UUID userId,

        // Email address of the user.
        @Schema(description = "User email", nullable = false)
        String email,

        // First name of the user (from profile).
        @Schema(description = "User first name")
        String firstName,

        // Last name of the user (from profile).
        @Schema(description = "User last name")
        String lastName,

        // Role assigned within the organization.
        @Schema(description = "Member role", nullable = false)
        OrganizationMemberRole role,

        // Membership status within the organization.
        @Schema(description = "Member status", nullable = false)
        OrganizationMemberStatus status
) {
}
