package com.korur.oba.apps.kaizen.pullrequest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kaizen.pullrequest.enums.PullRequestShareStatus;

/**
 * Request payload used to share a GitHub pull request in the pull requests
 * channel. It names the pull request only by repository ID and number: the
 * title, branches and GitHub author are read from GitHub, never taken from
 * the client.
 */
@Schema(description = "Pull request share create request")
public record PullRequestShareCreateRequest(

        // GitHub's ID of the repository; it must be one of the GitHub App installation's repositories.
        @Schema(description = "GitHub repository id, from the repository list", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "812345678")
        @NotNull(message = "GitHub repository id is required")
        @Positive(message = "GitHub repository id must be positive")
        Long githubRepositoryId,

        // Number of the pull request in that repository.
        @Schema(description = "Pull request number", requiredMode = Schema.RequiredMode.REQUIRED, example = "379")
        @NotNull(message = "Pull request number is required")
        @Positive(message = "Pull request number must be positive")
        Integer number,

        // Review state the author gives the pull request.
        @Schema(description = "Review status chosen by the author", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "IN_REVIEW")
        @NotNull(message = "Status is required")
        PullRequestShareStatus status,

        // Optional plain-text note after the status; blank is stored as null.
        @Schema(description = "Note shown after the status", nullable = true, example = "Merged for testing in prod")
        @Size(max = PullRequestShareConstant.MAX_NOTE_LENGTH, message = "Note must be at most 500 characters")
        String note,

        // Members asked to review, in display order; empty for none, never the author.
        @Schema(description = "User ids of the reviewers in display order; empty for none",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f\"]")
        @NotNull(message = "Reviewer ids are required; send an empty list for none")
        @Size(max = PullRequestShareConstant.MAX_REVIEWERS, message = "A share can name at most 10 reviewers")
        List<@NotNull UUID> reviewerIds,

        // Tickets the author relates the pull request to, in display order; empty for none.
        // Each must be live and on a board the author is a member of.
        @Schema(description = "Ticket ids of the related tickets in display order; empty for none",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70\"]")
        @NotNull(message = "Related ticket ids are required; send an empty list for none")
        @Size(max = PullRequestShareConstant.MAX_RELATED_TICKETS,
                message = "A share can name at most 10 related tickets")
        List<@NotNull UUID> relatedTicketIds
) {
}
