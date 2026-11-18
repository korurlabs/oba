package com.korur.oba.apps.kaizen.pullrequest.mapper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.GithubPullRequestQueryRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.request.PullRequestShareUpdateRequest;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubContributorResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubPullRequestPageResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareTicketResponse;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareTicket;
import com.korur.oba.apps.kaizen.pullrequest.enums.PullRequestShareStatus;
import com.korur.oba.apps.kaizen.pullrequest.mock_data.PullRequestMocks;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.connect.github.dto.GithubContributorPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestFilter;
import com.korur.oba.connect.github.dto.GithubPullRequestPagePayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.enums.GithubPullRequestState;
import com.korur.oba.shrine.identity.entity.User;

class PullRequestShareMapperUnitTest {

    private final PullRequestShareMapper mapper = new PullRequestShareMapperImpl();

    @Test
    void toEntity_createRequest_takesGithubFactsFromGithubAndUppercasesBranchKey() {
        // Arrange
        UUID organizationId = UUID.randomUUID();
        GithubPullRequestPayload pullRequest = PullRequestMocks.pullRequest(379, "Mobile menu",
                "feature/acme-241-mobile-menu");

        // Act
        PullRequestShare share = mapper.toEntity(
                PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, "  ", List.of()), organizationId,
                PullRequestMocks.repository(), pullRequest);

        // Assert
        assertThat(share.getOrganizationId()).isEqualTo(organizationId);
        assertThat(share.getGithubRepositoryId()).isEqualTo(PullRequestMocks.GITHUB_REPOSITORY_ID);
        assertThat(share.getRepositoryFullName()).isEqualTo(PullRequestMocks.REPOSITORY_FULL_NAME);
        assertThat(share.getNumber()).isEqualTo(379);
        assertThat(share.getTitle()).isEqualTo("Mobile menu");
        assertThat(share.getHeadRef()).isEqualTo("feature/acme-241-mobile-menu");
        assertThat(share.getTicketKey()).isEqualTo("ACME-241");
        assertThat(share.getStatus()).isEqualTo(PullRequestShareStatus.OPEN);
        assertThat(share.getNote()).as("a blank note is stored as null").isNull();
        assertThat(share.getAuthor()).isNull();
        assertThat(share.getEditedAt()).isNull();
    }

    @Test
    void toEntity_longGithubTexts_areCutToTheColumnLengthByCodePoints() {
        // Arrange
        String emojiTitle = "🚀".repeat(501);
        GithubPullRequestPayload pullRequest = PullRequestMocks.pullRequest(1, emojiTitle, "b".repeat(300));

        // Act
        PullRequestShare share = mapper.toEntity(
                PullRequestMocks.createRequest(PullRequestShareStatus.OPEN, "kept", List.of()),
                UUID.randomUUID(), PullRequestMocks.repository(), pullRequest);

        // Assert
        assertThat(share.getTitle().codePointCount(0, share.getTitle().length())).isEqualTo(500);
        assertThat(share.getTitle()).isEqualTo("🚀".repeat(500));
        assertThat(share.getHeadRef()).hasSize(255);
        assertThat(share.getNote()).isEqualTo("kept");
    }

    @Test
    void toTicketKey_headBranchAndTitleBothHoldKeys_branchWins() {
        // Act and Assert
        assertThat(mapper.toTicketKey(PullRequestMocks.pullRequest(1, "ABC-9 other", "feature/acme-241-x")))
                .isEqualTo("ACME-241");
    }

    @Test
    void toTicketKey_onlyTitleHoldsKey_titleKeyIsUsed() {
        // Act and Assert
        assertThat(mapper.toTicketKey(PullRequestMocks.pullRequest(1, "ABC-9 other", "feature/menu")))
                .isEqualTo("ABC-9");
    }

    @Test
    void toTicketKey_noKeyOrNull_returnsNull() {
        // Act and Assert
        assertThat(mapper.toTicketKey(PullRequestMocks.pullRequest(1, "no key", "feature/menu"))).isNull();
        assertThat(mapper.toTicketKey(null)).isNull();
    }

    @Test
    void updateEntity_editRequest_changesStatusAndNoteOnlyAndClearsBlankNote() {
        // Arrange
        User author = PullRequestMocks.user("Ada", "Lovelace");
        PullRequestShare share = PullRequestMocks.share(author, UUID.randomUUID());
        share.setNote("old");
        PullRequestShareUpdateRequest request = PullRequestMocks.updateRequest(PullRequestShareStatus.MERGED, " ",
                List.of());

        // Act
        mapper.updateEntity(share, request);

        // Assert
        assertThat(share.getStatus()).isEqualTo(PullRequestShareStatus.MERGED);
        assertThat(share.getNote()).isNull();
        assertThat(share.getTitle()).isEqualTo("Mobile menu");
        assertThat(share.getTicketKey()).isEqualTo("ACME-241");
        assertThat(share.getAuthor()).isSameAs(author);
    }

    @Test
    void toReviewerEntity_user_buildsUnapprovedRowAtPosition() {
        // Arrange
        User reviewer = PullRequestMocks.user("Alan", "Turing");

        // Act
        PullRequestShareReviewer row = mapper.toReviewerEntity(reviewer, 2);

        // Assert
        assertThat(row.getUser()).isSameAs(reviewer);
        assertThat(row.getPosition()).isEqualTo(2);
        assertThat(row.getApprovedAt()).isNull();
        assertThat(row.getShare()).isNull();
    }

    @Test
    void toTicketEntity_ticket_snapshotsKeyTitleAndBoardAtPosition() {
        // Arrange
        Board board = PullRequestMocks.board("Fabrikam");
        Ticket ticket = PullRequestMocks.ticket(board, "ACME-12", "Fix menu");

        // Act
        PullRequestShareTicket row = mapper.toTicketEntity(ticket, 3);

        // Assert
        assertThat(row.getTicketId()).isEqualTo(ticket.getId());
        assertThat(row.getBoardId()).isEqualTo(board.getId());
        assertThat(row.getBoardName()).isEqualTo("Fabrikam");
        assertThat(row.getTicketKey()).isEqualTo("ACME-12");
        assertThat(row.getTicketTitle()).isEqualTo("Fix menu");
        assertThat(row.getPosition()).isEqualTo(3);
        assertThat(row.getShare()).isNull();
    }

    @Test
    void toTicketResponse_rowAndCanOpen_copiesTheSnapshotAndTheFlag() {
        // Arrange
        PullRequestShare share = PullRequestMocks.share(PullRequestMocks.user("Ada", "Lovelace"), UUID.randomUUID());
        PullRequestShareTicket row = PullRequestMocks.ticketRow(share,
                PullRequestMocks.ticket(PullRequestMocks.board("Fabrikam"), "ACME-12", "Fix menu"), 0);

        // Act
        PullRequestShareTicketResponse open = mapper.toTicketResponse(row, true);
        PullRequestShareTicketResponse closed = mapper.toTicketResponse(row, false);

        // Assert
        assertThat(open.ticketId()).isEqualTo(row.getTicketId());
        assertThat(open.boardId()).isEqualTo(row.getBoardId());
        assertThat(open.boardName()).isEqualTo("Fabrikam");
        assertThat(open.ticketKey()).isEqualTo("ACME-12");
        assertThat(open.ticketTitle()).isEqualTo("Fix menu");
        assertThat(open.canOpen()).isTrue();
        assertThat(closed.canOpen()).isFalse();
    }

    @Test
    void toChatMessageCreateRequest_share_hasNoTextAndShareIdAsNonce() {
        // Arrange
        PullRequestShare share = PullRequestMocks.share(PullRequestMocks.user("Ada", "Lovelace"),
                UUID.randomUUID());
        UUID channelId = UUID.randomUUID();

        // Act
        ChatMessageCreateRequest request = mapper.toChatMessageCreateRequest(share, channelId);

        // Assert
        assertThat(request.content()).isNull();
        assertThat(request.channelId()).isEqualTo(channelId);
        assertThat(request.pullRequestShareId()).isEqualTo(share.getId());
        assertThat(request.clientNonce()).isEqualTo(share.getId().toString());
        assertThat(request.threadId()).isNull();
        assertThat(request.replyToMessageId()).isNull();
        assertThat(request.dailyUpdateId()).isNull();
    }

    @Test
    void toShareUrl_share_isBuiltFromNameAndNumber() {
        // Act and Assert
        assertThat(mapper.toShareUrl(PullRequestMocks.share(PullRequestMocks.user("Ada", "Lovelace"),
                UUID.randomUUID()))).isEqualTo("https://github.com/senseylabs/fabrikam/pull/379");
    }

    @Test
    void toPullRequestFilter_copiesStatesAndAuthors() {
        // Arrange
        GithubPullRequestQueryRequest request = new GithubPullRequestQueryRequest();
        request.setStates(List.of(GithubPullRequestState.OPEN, GithubPullRequestState.MERGED));
        request.setAuthors(List.of("octo", "dependabot[bot]"));

        // Act
        GithubPullRequestFilter result = mapper.toPullRequestFilter(request);

        // Assert
        assertThat(result.states()).containsExactly(GithubPullRequestState.OPEN, GithubPullRequestState.MERGED);
        assertThat(result.authors()).containsExactly("octo", "dependabot[bot]");
    }

    @Test
    void toPullRequestFilter_noStatesOrAuthors_isTheEmptyFilter() {
        // Act
        GithubPullRequestFilter result = mapper.toPullRequestFilter(new GithubPullRequestQueryRequest());

        // Assert
        assertThat(result).isEqualTo(GithubPullRequestFilter.none());
    }

    @Test
    void toPageResponse_carriesResultsLimited() {
        // Arrange
        GithubPullRequestPagePayload page = new GithubPullRequestPagePayload(List.of(), 2, true, true);

        // Act
        GithubPullRequestPageResponse result = mapper.toPageResponse(page);

        // Assert
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.resultsLimited()).isTrue();
    }

    @Test
    void toContributorResponses_keepOrderAndLogins() {
        // Act
        List<GithubContributorResponse> result = mapper.toContributorResponses(
                List.of(new GithubContributorPayload("ada"), new GithubContributorPayload("github-actions[bot]")));

        // Assert
        assertThat(result).containsExactly(new GithubContributorResponse("ada"),
                new GithubContributorResponse("github-actions[bot]"));
        assertThat(mapper.toContributorResponses(List.of())).isEmpty();
    }
}
