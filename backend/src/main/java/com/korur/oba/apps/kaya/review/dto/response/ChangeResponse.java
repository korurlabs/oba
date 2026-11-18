package com.korur.oba.apps.kaya.review.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * One row of the changes list: a version with the role its approval needs and its reviews.
 */
@Schema(description = "A change: a version with the role its approval needs and its reviews")
public record ChangeResponse(
        @Schema(description = "The version, with its pack or project, status, source, proposer and reason")
        VersionResponse version,

        @Schema(description = "Least role that may approve it: OWNER for an executable change, otherwise ADMIN", example = "ADMIN")
        OrganizationMemberRole requiredRole,

        @Schema(description = "Reviews, oldest first")
        List<ReviewResponse> reviews
) {
}
