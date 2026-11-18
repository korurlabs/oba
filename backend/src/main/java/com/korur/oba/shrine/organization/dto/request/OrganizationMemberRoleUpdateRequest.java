package com.korur.oba.shrine.organization.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Request to update a member's role in an organization.
 */
@Schema(description = "Organization member role update request")
public record OrganizationMemberRoleUpdateRequest(
        @Schema(description = "New role for the member", nullable = false)
        @NotNull(message = "Role is required")
        OrganizationMemberRole role
) {
}
