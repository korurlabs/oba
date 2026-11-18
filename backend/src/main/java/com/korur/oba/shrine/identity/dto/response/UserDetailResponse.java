package com.korur.oba.shrine.identity.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.dto.response.embedded.UserOrganizationEmbeddedResponse;
import com.korur.oba.shrine.identity.dto.response.embedded.UserProfileEmbeddedDetailResponse;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.invitation.dto.response.InvitationResponse;

@Schema(description = "User detail response")
public record UserDetailResponse(

        @Schema(description = "User id", nullable = false)
        UUID id,

        @Schema(description = "Created at", nullable = false)
        Instant createdAt,

        @Schema(description = "Updated at", nullable = false)
        Instant updatedAt,

        @Schema(description = "Email address", nullable = false)
        String email,

        @Schema(description = "User status", nullable = false)
        UserStatus userStatus,

        @Schema(description = "User profile information", nullable = true)
        UserProfileEmbeddedDetailResponse profile,

        @Schema(description = "Default organization ID", nullable = true)
        UUID defaultOrganizationId,

        @Schema(description = "User preferences (opaque JSON, structure owned by frontend)", nullable = true)
        Map<String, Object> userPreferences,

        @Schema(description = "Organizations the user belongs to", nullable = true)
        List<UserOrganizationEmbeddedResponse> organizations,

        @Schema(description = "Pending invitations for this user", nullable = true)
        List<InvitationResponse> pendingInvitations
) {
}
