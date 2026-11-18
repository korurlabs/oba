package com.korur.oba.apps.kazan.pullrequest.mock_data;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareUpdateRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareReviewerResponse;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareTicket;
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.connect.github.enums.GithubPullRequestState;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Builders for the shares, GitHub payloads and requests the pull request share tests use.
 */
public final class PullRequestMocks {

    public static final long GITHUB_REPOSITORY_ID = 812345678L;
    public static final String REPOSITORY_FULL_NAME = "senseylabs/fabrikam";

    private PullRequestMocks() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static User user(String firstName, String lastName) {
        return ChatMocks.user(firstName, lastName);
    }

    public static GithubRepositoryPayload repository() {
        return new GithubRepositoryPayload(GITHUB_REPOSITORY_ID, REPOSITORY_FULL_NAME, true);
    }

    public static GithubPullRequestPayload pullRequest(int number, String title, String headRef) {
        return new GithubPullRequestPayload(number, title, GithubPullRequestState.OPEN, "ada-dev",
                Instant.parse("2026-10-27T08:00:00Z"), "https://github.com/" + REPOSITORY_FULL_NAME + "/pull/" + number,
                headRef, "main");
    }

    public static PullRequestShareCreateRequest createRequest(PullRequestShareStatus status, String note,
                                                              List<UUID> reviewerIds) {
        return new PullRequestShareCreateRequest(GITHUB_REPOSITORY_ID, 379, status, note, new ArrayList<>(reviewerIds),
                new ArrayList<>());
    }

    public static PullRequestShareUpdateRequest updateRequest(PullRequestShareStatus status, String note,
                                                              List<UUID> reviewerIds) {
        return new PullRequestShareUpdateRequest(status, note, new ArrayList<>(reviewerIds), new ArrayList<>());
    }

    public static PullRequestShareCreateRequest createRequestWithTickets(List<UUID> relatedTicketIds) {
        return new PullRequestShareCreateRequest(GITHUB_REPOSITORY_ID, 379, PullRequestShareStatus.OPEN, null,
                new ArrayList<>(), new ArrayList<>(relatedTicketIds));
    }

    public static PullRequestShareUpdateRequest updateRequestWithTickets(List<UUID> relatedTicketIds) {
        return new PullRequestShareUpdateRequest(PullRequestShareStatus.OPEN, null, new ArrayList<>(),
                new ArrayList<>(relatedTicketIds));
    }

    public static Board board(String name) {
        Board board = Board.builder().name(name).prefix("B").color("#112233").build();
        board.setId(UUID.randomUUID());
        return board;
    }

    public static Ticket ticket(Board board, String key, String title) {
        Ticket ticket = Ticket.builder().key(key).title(title).board(board).build();
        ticket.setId(UUID.randomUUID());
        return ticket;
    }

    public static PullRequestShareTicket ticketRow(PullRequestShare share, Ticket ticket, int position) {
        PullRequestShareTicket row = PullRequestShareTicket.builder()
                .share(share)
                .ticketId(ticket.getId())
                .boardId(ticket.getBoard().getId())
                .ticketKey(ticket.getKey())
                .ticketTitle(ticket.getTitle())
                .boardName(ticket.getBoard().getName())
                .position(position)
                .build();
        row.setId(UUID.randomUUID());
        share.getRelatedTickets().add(row);
        return row;
    }

    public static PullRequestShare share(User author, UUID organizationId) {
        PullRequestShare share = PullRequestShare.builder()
                .organizationId(organizationId)
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .repositoryFullName(REPOSITORY_FULL_NAME)
                .number(379)
                .title("Mobile menu")
                .githubAuthorLogin("ada-dev")
                .headRef("feature/ACME-241-mobile-menu")
                .baseRef("main")
                .ticketKey("ACME-241")
                .status(PullRequestShareStatus.IN_REVIEW)
                .author(author)
                .reviewers(new ArrayList<>())
                .build();
        share.setId(UUID.randomUUID());
        share.setCreatedAt(Instant.parse("2026-10-27T09:00:00Z"));
        return share;
    }

    public static PullRequestShareReviewer reviewer(PullRequestShare share, User user, int position,
                                                    Instant approvedAt) {
        PullRequestShareReviewer reviewer = PullRequestShareReviewer.builder()
                .share(share)
                .user(user)
                .position(position)
                .approvedAt(approvedAt)
                .build();
        reviewer.setId(UUID.randomUUID());
        share.getReviewers().add(reviewer);
        return reviewer;
    }

    public static PullRequestShareResponse shareResponse(UUID shareId, User author,
                                                         List<PullRequestShareReviewerResponse> reviewers) {
        return new PullRequestShareResponse(shareId, author.getId(), author.getProfile().getFirstName(),
                author.getProfile().getLastName(), REPOSITORY_FULL_NAME, 379, "Mobile menu",
                "https://github.com/" + REPOSITORY_FULL_NAME + "/pull/379", "ada-dev",
                "feature/ACME-241-mobile-menu", "main", "ACME-241", null, PullRequestShareStatus.IN_REVIEW, null,
                reviewers, List.of(), Instant.parse("2026-10-27T09:00:00Z"), null,
                "Mobile menu\nhttps://github.com/" + REPOSITORY_FULL_NAME + "/pull/379\nStatus: In Review", null, null);
    }

    public static PullRequestShareReviewerResponse reviewerResponse(User user, Instant approvedAt) {
        return new PullRequestShareReviewerResponse(user.getId(), user.getProfile().getFirstName(),
                user.getProfile().getLastName(), approvedAt);
    }
}
