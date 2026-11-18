package com.korur.oba.apps.kazan.pullrequest.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

// 3. Java SDK
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.chat.dto.request.ChatMessageCreateRequest;
import com.korur.oba.apps.kazan.chat.dto.response.ChatLinkedTicketResponse;
import com.korur.oba.apps.kazan.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kazan.pullrequest.dto.request.GithubPullRequestQueryRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareCreateRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.request.PullRequestShareUpdateRequest;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubContributorResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestPageResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubPullRequestSummaryResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.GithubRepositoryResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestSharePreviewResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareReviewerResponse;
import com.korur.oba.apps.kazan.pullrequest.dto.response.PullRequestShareTicketResponse;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kazan.pullrequest.entity.PullRequestShareTicket;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.connect.github.constant.GithubConstant;
import com.korur.oba.connect.github.dto.GithubContributorPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestFilter;
import com.korur.oba.connect.github.dto.GithubPullRequestPagePayload;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.connect.github.dto.GithubPullRequestSummaryPayload;
import com.korur.oba.connect.github.dto.GithubRepositoryPayload;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Maps pull request shares, their reviewers, their related tickets and the
 * GitHub picker payloads between requests, entities and responses.
 *
 * A new share takes its GitHub facts from the repository and pull request
 * read from GitHub, never from the request, which names the pull request only
 * by repository ID and number. Texts longer than their column are cut, so a
 * long GitHub title or branch never fails the save. A blank note is stored as
 * null. The author, the reviewer rows and the related ticket rows are set by
 * {@link PullRequestShareMapperHelper}; editedAt is set by the services. The
 * link is built from the repository name and the number, as GitHub's own
 * html_url is never used.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PullRequestShareMapper {

    // -------------Entities-----------//

    /**
     * Maps a create request and the pull request read from GitHub to a new
     * share without author, reviewers or related tickets.
     *
     * @param request        the create request, for status and note
     * @param organizationId organization whose pull requests channel shows the share
     * @param repository     the installation's repository the request names
     * @param pullRequest    the pull request as GitHub returned it
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "reviewers", ignore = true)
    @Mapping(target = "relatedTickets", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "githubRepositoryId", source = "repository.githubRepositoryId")
    @Mapping(target = "repositoryFullName", source = "repository.fullName")
    @Mapping(target = "number", source = "pullRequest.number")
    @Mapping(target = "title", source = "pullRequest.title", qualifiedByName = "toStoredTitle")
    @Mapping(target = "githubAuthorLogin", source = "pullRequest.authorLogin", qualifiedByName = "toStoredLogin")
    @Mapping(target = "headRef", source = "pullRequest.headRef", qualifiedByName = "toStoredRef")
    @Mapping(target = "baseRef", source = "pullRequest.baseRef", qualifiedByName = "toStoredRef")
    @Mapping(target = "ticketKey", source = "pullRequest", qualifiedByName = "toTicketKey")
    @Mapping(target = "status", source = "request.status")
    @Mapping(target = "note", source = "request.note", qualifiedByName = "blankToNull")
    PullRequestShare toEntity(PullRequestShareCreateRequest request, UUID organizationId,
                              GithubRepositoryPayload repository, GithubPullRequestPayload pullRequest);

    /**
     * Replaces status and note with the edit request's values; null or blank
     * clears the note. Reviewers and related tickets are applied by
     * {@link PullRequestShareMapperHelper#setUpdateRelations}; the GitHub facts never change.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "editedAt", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "reviewers", ignore = true)
    @Mapping(target = "relatedTickets", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "githubRepositoryId", ignore = true)
    @Mapping(target = "repositoryFullName", ignore = true)
    @Mapping(target = "number", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "githubAuthorLogin", ignore = true)
    @Mapping(target = "headRef", ignore = true)
    @Mapping(target = "baseRef", ignore = true)
    @Mapping(target = "ticketKey", ignore = true)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "note", source = "note", qualifiedByName = "blankToNull")
    void updateEntity(@MappingTarget PullRequestShare share, PullRequestShareUpdateRequest request);

    /**
     * Builds a new, unapproved reviewer row that is not attached to a share.
     *
     * @param user     the member asked to review
     * @param position zero-based place in the share's list
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "share", ignore = true)
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "position", source = "position")
    PullRequestShareReviewer toReviewerEntity(User user, int position);

    /**
     * Builds a new related ticket row that is not attached to a share. The
     * ticket's key, title, board ID and board name are copied as a snapshot,
     * which the row keeps for as long as the ticket stays on the share.
     *
     * @param ticket   a ticket the author can open
     * @param position zero-based place in the share's list
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "share", ignore = true)
    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "boardId", source = "ticket.board.id")
    @Mapping(target = "boardName", source = "ticket.board.name")
    @Mapping(target = "ticketKey", source = "ticket.key")
    @Mapping(target = "ticketTitle", source = "ticket.title")
    @Mapping(target = "position", source = "position")
    PullRequestShareTicket toTicketEntity(Ticket ticket, int position);

    // -------------Chat entry-----------//

    /**
     * Builds the chat send request of a new share's entry in the pull
     * requests channel. The entry has no text, because the card of the share
     * is its content, and no thread or quote. The share ID is the client
     * nonce: the server sends the entry, so no pending row waits for it, and
     * the ID is unique per entry.
     *
     * @param share     the saved share
     * @param channelId the organization's pull requests channel
     */
    @Mapping(target = "content", ignore = true)
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "threadId", ignore = true)
    @Mapping(target = "replyToMessageId", ignore = true)
    @Mapping(target = "dailyUpdateId", ignore = true)
    @Mapping(target = "pullRequestShareId", source = "share.id")
    @Mapping(target = "clientNonce", source = "share.id", qualifiedByName = "toClientNonce")
    ChatMessageCreateRequest toChatMessageCreateRequest(PullRequestShare share, UUID channelId);

    // -------------Share responses-----------//

    /**
     * Maps a reviewer row with the reviewer's name and approval.
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "firstName", source = "user.profile.firstName")
    @Mapping(target = "lastName", source = "user.profile.lastName")
    @Mapping(target = "approvedAt", source = "approvedAt")
    PullRequestShareReviewerResponse toReviewerResponse(PullRequestShareReviewer reviewer);

    /**
     * Maps a related ticket row with its stored snapshot and the reader's
     * canOpen flag.
     *
     * @param row     the stored row
     * @param canOpen true when the ticket is live and on a board the reader is a member of
     */
    @Mapping(target = "ticketId", source = "row.ticketId")
    @Mapping(target = "boardId", source = "row.boardId")
    @Mapping(target = "boardName", source = "row.boardName")
    @Mapping(target = "ticketKey", source = "row.ticketKey")
    @Mapping(target = "ticketTitle", source = "row.ticketTitle")
    @Mapping(target = "canOpen", source = "canOpen")
    PullRequestShareTicketResponse toTicketResponse(PullRequestShareTicket row, boolean canOpen);

    /**
     * Maps a share with its mapped reviewers and related tickets, the
     * reader's linked ticket, the summary text and the location of its chat
     * entry.
     *
     * @param share          the share
     * @param reviewers      its reviewers in display order
     * @param relatedTickets its related tickets in display order, with the reader's canOpen
     * @param linkedTicket   the ticket of the share's key when the reader can open it, else null
     * @param summaryText    the share's plain text in the team's format
     * @param channelId      channel of the share's live chat entry, else null
     * @param messageId      the share's live chat entry, else null
     */
    @Mapping(target = "id", source = "share.id")
    @Mapping(target = "authorId", source = "share.author.id")
    @Mapping(target = "authorFirstName", source = "share.author.profile.firstName")
    @Mapping(target = "authorLastName", source = "share.author.profile.lastName")
    @Mapping(target = "repositoryFullName", source = "share.repositoryFullName")
    @Mapping(target = "number", source = "share.number")
    @Mapping(target = "title", source = "share.title")
    @Mapping(target = "url", source = "share", qualifiedByName = "toShareUrl")
    @Mapping(target = "githubAuthorLogin", source = "share.githubAuthorLogin")
    @Mapping(target = "headRef", source = "share.headRef")
    @Mapping(target = "baseRef", source = "share.baseRef")
    @Mapping(target = "ticketKey", source = "share.ticketKey")
    @Mapping(target = "linkedTicket", source = "linkedTicket")
    @Mapping(target = "status", source = "share.status")
    @Mapping(target = "note", source = "share.note")
    @Mapping(target = "reviewers", source = "reviewers")
    @Mapping(target = "relatedTickets", source = "relatedTickets")
    @Mapping(target = "createdAt", source = "share.createdAt")
    @Mapping(target = "editedAt", source = "share.editedAt")
    @Mapping(target = "summaryText", source = "summaryText")
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "messageId", source = "messageId")
    PullRequestShareResponse toResponse(PullRequestShare share, List<PullRequestShareReviewerResponse> reviewers,
                                        List<PullRequestShareTicketResponse> relatedTickets,
                                        ChatLinkedTicketResponse linkedTicket, String summaryText, UUID channelId,
                                        UUID messageId);

    /**
     * Maps a ticket the reader can open, keeping the share's upper-case key.
     */
    @Mapping(target = "ticketKey", source = "ticketKey")
    @Mapping(target = "ticketId", source = "ticket.id")
    @Mapping(target = "boardId", source = "ticket.board.id")
    ChatLinkedTicketResponse toLinkedTicketResponse(String ticketKey, Ticket ticket);

    // -------------GitHub picker responses-----------//

    /**
     * Maps one repository of the installation.
     */
    @Mapping(target = "githubRepositoryId", source = "githubRepositoryId")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "privateRepository", source = "privateRepository")
    GithubRepositoryResponse toRepositoryResponse(GithubRepositoryPayload repository);

    /**
     * Maps the installation's repositories, keeping their order.
     */
    List<GithubRepositoryResponse> toRepositoryResponses(List<GithubRepositoryPayload> repositories);

    /**
     * Maps one contributor of a repository.
     */
    @Mapping(target = "login", source = "login")
    GithubContributorResponse toContributorResponse(GithubContributorPayload contributor);

    /**
     * Maps a repository's contributors, keeping their order.
     */
    List<GithubContributorResponse> toContributorResponses(List<GithubContributorPayload> contributors);

    /**
     * Maps the picker's filters for the GitHub client. Validate the authors
     * first: they become part of a GitHub search query.
     */
    @Mapping(target = "states", source = "states")
    @Mapping(target = "authors", source = "authors")
    GithubPullRequestFilter toPullRequestFilter(GithubPullRequestQueryRequest request);

    /**
     * Maps one picker row.
     */
    @Mapping(target = "number", source = "number")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "authorLogin", source = "authorLogin")
    @Mapping(target = "updatedAt", source = "updatedAt")
    @Mapping(target = "url", source = "url")
    GithubPullRequestSummaryResponse toSummaryResponse(GithubPullRequestSummaryPayload pullRequest);

    /**
     * Maps one picker page.
     */
    @Mapping(target = "items", source = "items")
    @Mapping(target = "page", source = "page")
    @Mapping(target = "hasMore", source = "hasMore")
    @Mapping(target = "resultsLimited", source = "resultsLimited")
    GithubPullRequestPageResponse toPageResponse(GithubPullRequestPagePayload page);

    /**
     * Maps one pull request for the share preview, with the ticket key of its
     * branch or title and the reader's linked ticket.
     *
     * @param pullRequest  the pull request as GitHub returned it
     * @param linkedTicket the ticket of the key when the reader can open it, else null
     */
    @Mapping(target = "number", source = "pullRequest.number")
    @Mapping(target = "title", source = "pullRequest.title")
    @Mapping(target = "state", source = "pullRequest.state")
    @Mapping(target = "authorLogin", source = "pullRequest.authorLogin")
    @Mapping(target = "updatedAt", source = "pullRequest.updatedAt")
    @Mapping(target = "url", source = "pullRequest.url")
    @Mapping(target = "headRef", source = "pullRequest.headRef")
    @Mapping(target = "baseRef", source = "pullRequest.baseRef")
    @Mapping(target = "ticketKey", source = "pullRequest", qualifiedByName = "toTicketKey")
    @Mapping(target = "linkedTicket", source = "linkedTicket")
    GithubPullRequestResponse toPullRequestResponse(GithubPullRequestPayload pullRequest,
                                                    ChatLinkedTicketResponse linkedTicket);

    /**
     * Maps a share preview: the pull request and the text its card would show.
     *
     * @param pullRequest the pull request as the picker shows it
     * @param summaryText the card's plain text in the team's format
     */
    @Mapping(target = "pullRequest", source = "pullRequest")
    @Mapping(target = "summaryText", source = "summaryText")
    PullRequestSharePreviewResponse toPreviewResponse(GithubPullRequestResponse pullRequest, String summaryText);

    // -------------Derived values-----------//

    /**
     * Finds the ticket key of a pull request: the first key in the head
     * branch, else the first in the title, upper-cased. Branches are read
     * first because the team names them after the ticket, while a title may
     * mention other tickets.
     *
     * @param pullRequest the pull request as GitHub returned it
     * @return the key, or null when neither holds one
     */
    @Named("toTicketKey")
    default String toTicketKey(GithubPullRequestPayload pullRequest) {
        if (pullRequest == null) {
            return null;
        }
        String key = findTicketKey(pullRequest.headRef());
        return key != null ? key : findTicketKey(pullRequest.title());
    }

    /**
     * Builds the github.com link of a share from its repository name and number.
     *
     * @param share the share
     * @return "https://github.com/{repositoryFullName}/pull/{number}"
     */
    @Named("toShareUrl")
    default String toShareUrl(PullRequestShare share) {
        return GithubConstant.WEB_BASE_URL + share.getRepositoryFullName()
                + GithubConstant.PULL_REQUEST_URL_SEGMENT + share.getNumber();
    }

    /**
     * Writes a share ID as the client nonce of its chat entry.
     */
    @Named("toClientNonce")
    default String toClientNonce(UUID shareId) {
        return shareId.toString();
    }

    /**
     * Turns a blank text into null and keeps any other text as written.
     */
    @Named("blankToNull")
    default String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Cuts a GitHub title to the stored length.
     */
    @Named("toStoredTitle")
    default String toStoredTitle(String title) {
        return cut(title, PullRequestShareConstant.MAX_TITLE_LENGTH);
    }

    /**
     * Cuts a GitHub login to the stored length.
     */
    @Named("toStoredLogin")
    default String toStoredLogin(String login) {
        return cut(login, PullRequestShareConstant.MAX_LOGIN_LENGTH);
    }

    /**
     * Cuts a branch name to the stored length.
     */
    @Named("toStoredRef")
    default String toStoredRef(String ref) {
        return cut(ref, PullRequestShareConstant.MAX_REF_LENGTH);
    }

    /**
     * Returns the first ticket key in the text, upper-cased, or null.
     */
    private static String findTicketKey(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = ChatConstant.TICKET_KEY_PATTERN.matcher(text);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    /**
     * Cuts a text to at most maxLength characters. Characters are counted as
     * code points, as PostgreSQL counts VARCHAR length, so the cut never
     * splits an emoji into half a surrogate pair.
     */
    private static String cut(String value, int maxLength) {
        if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }
}
