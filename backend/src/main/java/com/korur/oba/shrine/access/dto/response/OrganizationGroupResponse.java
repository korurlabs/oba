package com.korur.oba.shrine.access.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;

/**
 * Response payload describing a group of an organization with its member count and the apps granted to
 * it.
 */
@Schema(description = "Organization group response")
public record OrganizationGroupResponse(

        @Schema(description = "Group id", example = "8d0f5c1e-3b7a-4f2e-9c61-5a4b2e7d9f10")
        UUID id,

        @Schema(description = "Group name", example = "Engineering")
        String name,

        @Schema(description = "Group description", nullable = true, example = "Developers and testers")
        String description,

        @Schema(description = "Group type; EVERYONE cannot be renamed, deleted or given members",
                example = "CUSTOM")
        OrganizationGroupType type,

        // For EVERYONE, the ACTIVE members of the organization; for CUSTOM, the member rows of the group.
        @Schema(description = "Number of members in the group", example = "4")
        long memberCount,

        @Schema(description = "Apps granted to the group, in app order", example = "[\"KAZAN\"]")
        List<App> apps,

        @Schema(description = "Creation time (UTC)", example = "2026-11-09T10:15:30Z")
        Instant createdAt
) {
}
