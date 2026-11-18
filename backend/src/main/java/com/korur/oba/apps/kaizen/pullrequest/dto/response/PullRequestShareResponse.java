package com.korur.oba.apps.kaizen.pullrequest.dto.response;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.response.ChatLinkedTicketResponse;
import com.korur.oba.apps.kaizen.pullrequest.enums.PullRequestShareStatus;

/**
 * Response payload for a shared pull request, on its own or as the card of
 * its chat entry. Everything but linkedTicket and the canOpen of each
 * related ticket is the same for every reader.
 * channelId and messageId name the share's live chat entry, so a client can
 * link to the card; both are null when the entry is deleted or missing.
 */
@Schema(description = "Pull request share response")
public record PullRequestShareResponse(

        @Schema(description = "Share id", example = "5b2e7c9a-1d3f-4a6b-8c0d-2e4f6a8b0c1d")
        UUID id,

        @Schema(description = "User id of the member who shared it", example = "9a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9")
        UUID authorId,

        @Schema(description = "First name of the member who shared it", nullable = true, example = "Ada")
        String authorFirstName,

        @Schema(description = "Last name of the member who shared it", nullable = true, example = "Lovelace")
        String authorLastName,

        @Schema(description = "Repository as owner/name at share time", example = "korurlabs/village")
        String repositoryFullName,

        @Schema(description = "Pull request number", example = "379")
        int number,

        @Schema(description = "Title at share time, at most 500 characters", example = "ACME-241: Sidebar refactor")
        String title,

        @Schema(description = "Link to the pull request on github.com", example = "https://github.com/korurlabs/village/pull/379")
        String url,

        @Schema(description = "GitHub login of the pull request's author", example = "jane-doe")
        String githubAuthorLogin,

        @Schema(description = "Branch the pull request merges from", example = "feat/ACME-241-sidebar")
        String headRef,

        @Schema(description = "Branch the pull request merges into", example = "main")
        String baseRef,

        @Schema(description = "Upper-case ticket key in the head branch, else in the title", nullable = true,
                example = "ACME-241")
        String ticketKey,

        @Schema(description = "The ticket of ticketKey when this reader can open it", nullable = true)
        ChatLinkedTicketResponse linkedTicket,

        @Schema(description = "Review status chosen by the author", example = "IN_REVIEW")
        PullRequestShareStatus status,

        @Schema(description = "Note shown after the status", nullable = true, example = "Merged for testing in prod")
        String note,

        @Schema(description = "Reviewers in display order")
        List<PullRequestShareReviewerResponse> reviewers,

        @Schema(description = "Related tickets in display order, as stored when each was added; empty for none")
        List<PullRequestShareTicketResponse> relatedTickets,

        @Schema(description = "Share time (UTC)", example = "2026-10-28T08:15:00Z")
        Instant createdAt,

        @Schema(description = "Time of the author's last edit (UTC); null when never edited", nullable = true,
                example = "2026-10-28T09:02:00Z")
        Instant editedAt,

        @Schema(description = "Plain text of the share as the team posts it, the same for every reader",
                example = "ACME-241: Sidebar refactor\nhttps://github.com/korurlabs/village/pull/379\nStatus: In Review")
        String summaryText,

        @Schema(description = "Chat channel of the share's card; null when the card is gone", nullable = true,
                example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
        UUID channelId,

        @Schema(description = "Chat message of the share's card, for a link to it; null when the card is gone",
                nullable = true, example = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b")
        UUID messageId
) {
}
