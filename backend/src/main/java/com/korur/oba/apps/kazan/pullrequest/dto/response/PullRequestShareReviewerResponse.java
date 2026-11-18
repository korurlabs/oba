package com.korur.oba.apps.kazan.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.UUID;

// 4. Internal

/**
 * Response payload for one reviewer of a pull request share.
 */
@Schema(description = "Pull request share reviewer response")
public record PullRequestShareReviewerResponse(

        @Schema(description = "User id of the reviewer", example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID userId,

        @Schema(description = "Reviewer first name", nullable = true, example = "Ayse")
        String firstName,

        @Schema(description = "Reviewer last name", nullable = true, example = "Smith")
        String lastName,

        @Schema(description = "Time of the approval (UTC); null while not approved", nullable = true,
                example = "2026-10-28T09:12:00Z")
        Instant approvedAt
) {
}
