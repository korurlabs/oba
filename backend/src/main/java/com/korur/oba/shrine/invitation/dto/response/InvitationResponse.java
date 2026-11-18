package com.korur.oba.shrine.invitation.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.invitation.enums.InvitationStatus;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Response containing invitation details.
 */
@Schema(description = "Organization invitation response")
public record InvitationResponse(
        @Schema(description = "Invitation ID", nullable = false)
        UUID id,

        @Schema(description = "Organization ID", nullable = false)
        UUID organizationId,

        @Schema(description = "Organization name", nullable = false)
        String organizationName,

        @Schema(description = "Email of invited user", nullable = false)
        String email,

        @Schema(description = "Role assigned", nullable = false)
        OrganizationMemberRole role,

        @Schema(description = "Invitation status", nullable = false)
        InvitationStatus status,

        @Schema(description = "Email of user who sent invitation", nullable = false)
        String invitedByEmail,

        @Schema(description = "Acceptance timestamp", nullable = true)
        Instant acceptedAt,

        @Schema(description = "Expiry timestamp", nullable = true)
        Instant expiresAt,

        @Schema(description = "Declined timestamp", nullable = true)
        Instant declinedAt,

        @Schema(description = "Creation timestamp", nullable = false)
        Instant createdAt
) {
}
