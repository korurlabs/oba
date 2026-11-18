package com.korur.oba.apps.kaya.review.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;
import com.korur.oba.apps.kaya.version.dto.response.VersionResponse;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * A change for its review screen: the change, the rules it breaks now and what the caller may do.
 */
@Schema(description = "A change for its review screen: the change, the rules it breaks now and what the caller may do")
public record ChangeDetailResponse(
        @Schema(description = "The version, with its pack or project, status, source, proposer and reason")
        VersionResponse version,

        @Schema(description = "Least role that may approve it: OWNER for an executable change, otherwise ADMIN", example = "OWNER")
        OrganizationMemberRole requiredRole,

        @Schema(description = "Reviews, oldest first")
        List<ReviewResponse> reviews,

        @Schema(description = "Rules the version breaks in the repositories that compose its owner, as a submit or an "
                + "approval would check them now; empty when it may pass. Given for a version in review, and for a draft "
                + "when the caller proposed it; null otherwise",
                nullable = true)
        List<Violation> violations,

        @Schema(description = "Whether the caller may approve it now", example = "true")
        Boolean canApprove,

        @Schema(description = "Whether the caller may reject it or comment on it now", example = "true")
        Boolean canReview
) {
}
