package com.korur.oba.shrine.invitation.dto.request;

// 1. Framework-level
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Request to create an organization invitation.
 */
@Schema(description = "Organization invitation create request")
public record InvitationCreateRequest(
        @Schema(description = "Email address of the user to invite", nullable = false)
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @Schema(description = "Role to assign to the invited user", nullable = false)
        @NotNull(message = "Role is required")
        OrganizationMemberRole role
) {
}
