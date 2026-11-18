package com.korur.oba.shrine.access.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.constant.AccessConstant;

/**
 * Request to add users to a custom group. Each user must be a member of the organization; users already in
 * the group stay as they are.
 */
@Schema(description = "Organization group member add request")
public record OrganizationGroupMemberAddRequest(

        @Schema(description = "Users to add; members of the organization",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "[\"3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f\"]")
        @NotEmpty(message = "At least one user is required")
        @Size(max = AccessConstant.MAX_GROUP_MEMBERS_PER_REQUEST, message = "At most 100 users per request")
        List<@NotNull(message = "User id is required") UUID> userIds
) {
}
