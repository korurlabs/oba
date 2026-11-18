package com.korur.oba.apps.kaizen.pullrequest.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kaizen.pullrequest.enums.PullRequestShareStatus;

/**
 * Request payload used to edit a pull request share. It is the full new
 * content, not a patch: status and note are replaced, and reviewerIds and
 * relatedTicketIds list every reviewer and related ticket, so one is removed
 * by leaving it out. The pull request itself cannot change.
 */
@Schema(description = "Pull request share edit request")
public record PullRequestShareUpdateRequest(

        // Review state the author gives the pull request.
        @Schema(description = "Review status chosen by the author", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "MERGED")
        @NotNull(message = "Status is required")
        PullRequestShareStatus status,

        // Optional plain-text note after the status; null or blank clears it.
        @Schema(description = "Note shown after the status", nullable = true, example = "Merged for testing in prod")
        @Size(max = PullRequestShareConstant.MAX_NOTE_LENGTH, message = "Note must be at most 500 characters")
        String note,

        // Every reviewer in display order; a kept reviewer keeps their approval.
        @Schema(description = "User ids of the reviewers in display order; empty for none",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f\"]")
        @NotNull(message = "Reviewer ids are required; send an empty list for none")
        @Size(max = PullRequestShareConstant.MAX_REVIEWERS, message = "A share can name at most 10 reviewers")
        List<@NotNull UUID> reviewerIds,

        // Every related ticket in display order; a kept ticket keeps its stored snapshot,
        // and only an added ticket must be live and on a board the author is a member of.
        @Schema(description = "Ticket ids of the related tickets in display order; empty for none",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"3f6c1d2e-8b7a-4c1e-9f0a-2b3c4d5e6f70\"]")
        @NotNull(message = "Related ticket ids are required; send an empty list for none")
        @Size(max = PullRequestShareConstant.MAX_RELATED_TICKETS,
                message = "A share can name at most 10 related tickets")
        List<@NotNull UUID> relatedTicketIds
) {
}
