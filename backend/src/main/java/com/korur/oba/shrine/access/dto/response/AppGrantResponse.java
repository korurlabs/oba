package com.korur.oba.shrine.access.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.AppGrantTargetType;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;

/**
 * Response payload describing an app grant of an organization. The user fields are set for a
 * {@link AppGrantTargetType#USER} grant, the group fields for a {@link AppGrantTargetType#GROUP} grant.
 */
@Schema(description = "App grant response")
public record AppGrantResponse(

        @Schema(description = "Grant id", example = "5b1c7e2a-9d4f-4a3b-8e6c-1f2a3b4c5d6e")
        UUID id,

        @Schema(description = "Granted app", example = "KAZAN")
        App app,

        @Schema(description = "Kind of target", example = "GROUP")
        AppGrantTargetType targetType,

        @Schema(description = "Target user id; null for a group grant", nullable = true,
                example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID userId,

        @Schema(description = "Target user's first and last name, or email; null for a group grant",
                nullable = true, example = "Ada Lovelace")
        String userFullName,

        @Schema(description = "Target group id; null for a user grant", nullable = true,
                example = "8d0f5c1e-3b7a-4f2e-9c61-5a4b2e7d9f10")
        UUID groupId,

        @Schema(description = "Target group name; null for a user grant", nullable = true, example = "Everyone")
        String groupName,

        @Schema(description = "Target group type; null for a user grant", nullable = true, example = "EVERYONE")
        OrganizationGroupType groupType,

        @Schema(description = "Creation time (UTC)", example = "2026-11-09T10:15:30Z")
        Instant createdAt
) {
}
