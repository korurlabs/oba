package com.korur.oba.apps.kaizen.pullrequest.mapper;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.response.ChatLinkedTicketResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.service.ChatChannelQueryService;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.GithubPullRequestResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestSharePreviewResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareReviewerResponse;
import com.korur.oba.apps.kaizen.pullrequest.dto.response.PullRequestShareTicketResponse;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShare;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareReviewer;
import com.korur.oba.apps.kaizen.pullrequest.entity.PullRequestShareTicket;
import com.korur.oba.apps.kaizen.pullrequest.helper.PullRequestShareSummaryRenderer;
import com.korur.oba.apps.kaizen.pullrequest.validator.PullRequestShareValidator;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.connect.github.dto.GithubPullRequestPayload;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Sets the author, the reviewer rows and the related ticket rows of pull
 * request shares, and resolves the ticket a share or a pull request names for
 * one reader.
 *
 * Reviewers arrive as users the caller has already checked: active owners,
 * admins or members of the organization, not the author, each once. A kept
 * reviewer keeps their row and approval; only an added reviewer starts
 * unapproved. The linked ticket is resolved with one ticket query per call,
 * however many shares are mapped, and only for a live ticket on a board the
 * reader is a member of, so a key on a board the reader does not belong to
 * stays plain text and reveals nothing. The chat entries that locate the
 * cards are likewise resolved with one query per call.
 *
 * A related ticket is added only when the author can open it, and its row
 * keeps a snapshot of key, title and board; a kept ticket keeps its row and
 * snapshot without a new check. Every reader gets every row, and whether the
 * reader can open each ticket is resolved with one ticket query per call.
 */
@Component
@RequiredArgsConstructor
public class PullRequestShareMapperHelper {

    private final PullRequestShareMapper pullRequestShareMapper;
    private final TicketQueryService ticketQueryService;
    private final PullRequestShareSummaryRenderer pullRequestShareSummaryRenderer;
    private final ChatChannelQueryService chatChannelQueryService;
    private final PullRequestShareValidator pullRequestShareValidator;

    /**
     * Loads the related tickets a user adds to a share, indexed by ID, in one
     * query.
     *
     * @param userId         ID of the author
     * @param organizationId the share's organization; only its boards' tickets are found
     * @param ticketIds      IDs of the tickets being added
     * @return the tickets by ID; empty when ticketIds is empty
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError#NOT_TICKET_FOUND}
     *         when a ticket is deleted, on a board of another organization, or on a
     *         board the user is not a member of
     */
    public Map<UUID, Ticket> resolveAddedTickets(UUID userId, UUID organizationId, Collection<UUID> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        List<Ticket> tickets = ticketQueryService.getAccessibleTickets(userId, organizationId,
                new ArrayList<>(ticketIds));
        pullRequestShareValidator.ensureRelatedTicketsAccessible(ticketIds, tickets);
        Map<UUID, Ticket> ticketsById = new HashMap<>();
        for (Ticket ticket : tickets) {
            ticketsById.put(ticket.getId(), ticket);
        }
        return ticketsById;
    }

    /**
     * Loads the related tickets of a new share in the request's order, which
     * is their order on the card.
     *
     * @param userId         ID of the author
     * @param organizationId the share's organization; only its boards' tickets are found
     * @param ticketIds      the checked related ticket IDs, each once
     * @return the tickets in the order of ticketIds
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         {@link com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError#NOT_TICKET_FOUND}
     *         when a ticket is deleted, on a board of another organization, or on a
     *         board the user is not a member of
     */
    public List<Ticket> resolveRelatedTickets(UUID userId, UUID organizationId, List<UUID> ticketIds) {
        Map<UUID, Ticket> ticketsById = resolveAddedTickets(userId, organizationId, ticketIds);
        return ticketIds.stream().map(ticketsById::get).toList();
    }

    /**
     * Lists the IDs of an edit's related tickets that the share does not hold
     * yet. Only these need the author's access check.
     *
     * @param share            the stored share
     * @param relatedTicketIds the edit's related ticket IDs
     * @return the added IDs, in the edit's order
     */
    public List<UUID> findAddedTicketIds(PullRequestShare share, List<UUID> relatedTicketIds) {
        Set<UUID> storedIds = new HashSet<>();
        for (PullRequestShareTicket row : share.getRelatedTickets()) {
            storedIds.add(row.getTicketId());
        }
        return relatedTicketIds.stream()
                .filter(ticketId -> !storedIds.contains(ticketId))
                .toList();
    }

    /**
     * Sets the author, the reviewer rows and the related ticket rows of a new
     * share.
     *
     * @param share          the new share, from PullRequestShareMapper.toEntity
     * @param author         the current user
     * @param reviewers      the checked reviewers in display order
     * @param relatedTickets the tickets the author can open, in display order
     */
    public void setCreateRelations(PullRequestShare share, User author, List<User> reviewers,
                                   List<Ticket> relatedTickets) {
        share.setAuthor(author);
        for (int position = 0; position < reviewers.size(); position++) {
            PullRequestShareReviewer reviewer = pullRequestShareMapper.toReviewerEntity(reviewers.get(position), position);
            reviewer.setShare(share);
            share.getReviewers().add(reviewer);
        }
        for (int position = 0; position < relatedTickets.size(); position++) {
            PullRequestShareTicket row = pullRequestShareMapper.toTicketEntity(relatedTickets.get(position), position);
            row.setShare(share);
            share.getRelatedTickets().add(row);
        }
    }

    /**
     * Replaces the share's reviewers and related tickets with the edit's. A
     * kept reviewer keeps their row and approvedAt and moves to their new
     * place; an added reviewer gets a new, unapproved row; a reviewer left out
     * is removed by orphan removal, and their approval with them. Related
     * tickets follow the same rule: a kept ticket keeps its row and snapshot
     * and moves, an added ticket gets a new row with a fresh snapshot, and a
     * ticket left out is removed. The same row is kept for the same user or
     * ticket, because removing and re-inserting it would break the unique keys
     * on (share, user) and (share, ticket): Hibernate flushes inserts before
     * orphan deletes.
     *
     * Status and note are applied by PullRequestShareMapper.updateEntity.
     *
     * @param share            the stored share
     * @param reviewers        the checked reviewers in display order
     * @param relatedTicketIds the checked related ticket IDs in display order
     * @param added            the tickets of the IDs the share did not hold, from {@link #resolveAddedTickets}
     */
    public void setUpdateRelations(PullRequestShare share, List<User> reviewers, List<UUID> relatedTicketIds,
                                   Map<UUID, Ticket> added) {
        Map<UUID, PullRequestShareReviewer> stored = new HashMap<>();
        for (PullRequestShareReviewer reviewer : share.getReviewers()) {
            stored.put(reviewer.getUser().getId(), reviewer);
        }
        List<PullRequestShareReviewer> updated = new ArrayList<>();
        for (int position = 0; position < reviewers.size(); position++) {
            User user = reviewers.get(position);
            PullRequestShareReviewer reviewer = stored.get(user.getId());
            if (reviewer != null) {
                reviewer.setPosition(position);
            } else {
                reviewer = pullRequestShareMapper.toReviewerEntity(user, position);
                reviewer.setShare(share);
            }
            updated.add(reviewer);
        }
        // The managed collection is mutated, never replaced, so orphan removal
        // sees the reviewers that were left out.
        share.getReviewers().clear();
        share.getReviewers().addAll(updated);
        setUpdateTickets(share, relatedTicketIds, added);
    }

    /**
     * Lists the user IDs of the share's reviewers in display order. Taken
     * before an edit, it tells the chat which reviewers the edit gained and lost.
     *
     * @param share the share
     * @return the reviewer user IDs, in display order
     */
    public Set<UUID> getReviewerIds(PullRequestShare share) {
        Set<UUID> reviewerIds = new LinkedHashSet<>();
        for (PullRequestShareReviewer reviewer : share.getReviewers()) {
            reviewerIds.add(reviewer.getUser().getId());
        }
        return reviewerIds;
    }

    /**
     * Maps the share's reviewers in display order.
     *
     * @param share the share
     * @return one response per reviewer
     */
    public List<PullRequestShareReviewerResponse> toReviewerResponses(PullRequestShare share) {
        return share.getReviewers().stream()
                .map(pullRequestShareMapper::toReviewerResponse)
                .toList();
    }

    /**
     * Maps one share for one reader, with its reviewers, its related tickets,
     * the reader's linked ticket and the summary text.
     *
     * @param share    the share
     * @param readerId ID of the reader; null links no ticket
     * @return the response
     */
    public PullRequestShareResponse toResponse(PullRequestShare share, UUID readerId) {
        return toResponses(List.of(share), share.getOrganizationId(), readerId).getFirst();
    }

    /**
     * Maps shares of one organization for one reader, keeping their order.
     * The linked tickets of all shares are resolved in one query, which
     * related tickets the reader can open in one more, and their chat entries
     * in a third. Tickets are resolved only on the organization's boards.
     *
     * @param shares         the shares, all of the organization
     * @param organizationId the organization of the shares
     * @param readerId       ID of the reader; null links no ticket and opens no related ticket
     * @return one response per share, in the order of shares
     */
    public List<PullRequestShareResponse> toResponses(List<PullRequestShare> shares, UUID organizationId,
                                                      UUID readerId) {
        if (shares.isEmpty()) {
            return List.of();
        }
        Map<String, ChatLinkedTicketResponse> linkedTickets = loadLinkedTickets(readerId, organizationId,
                shares.stream().map(PullRequestShare::getTicketKey).toList());
        Set<UUID> openableTicketIds = loadOpenableTicketIds(readerId, organizationId, shares);
        Map<UUID, ChatMessage> messagesByShareId = chatChannelQueryService.findPullRequestShareMessages(
                shares.stream().map(PullRequestShare::getId).toList());
        List<PullRequestShareResponse> responses = new ArrayList<>();
        for (PullRequestShare share : shares) {
            ChatLinkedTicketResponse linkedTicket = share.getTicketKey() == null
                    ? null
                    : linkedTickets.get(share.getTicketKey());
            ChatMessage message = messagesByShareId.get(share.getId());
            responses.add(pullRequestShareMapper.toResponse(share, toReviewerResponses(share),
                    toTicketResponses(share, openableTicketIds), linkedTicket,
                    renderSummary(share), message == null ? null : message.getChannelId(),
                    message == null ? null : message.getId()));
        }
        return responses;
    }

    /**
     * Maps a page of one organization's shares for one reader, keeping the
     * paging metadata.
     *
     * @param page           the page of shares, all of the organization
     * @param organizationId the organization of the shares
     * @param readerId       ID of the reader; null links no ticket
     * @return the page of responses
     */
    public Page<PullRequestShareResponse> toResponsePage(Page<PullRequestShare> page, UUID organizationId,
                                                         UUID readerId) {
        return new PageImpl<>(toResponses(page.getContent(), organizationId, readerId), page.getPageable(),
                page.getTotalElements());
    }

    /**
     * Maps the preview of a new share: the pull request for the reader and
     * the text the unsaved share's card would show, rendered as a saved
     * share's card is.
     *
     * @param share       the unsaved share, from PullRequestShareMapper.toEntity
     * @param pullRequest the pull request as GitHub returned it
     * @param readerId    ID of the user previewing
     * @return the preview response
     */
    public PullRequestSharePreviewResponse toPreviewResponse(PullRequestShare share,
                                                             GithubPullRequestPayload pullRequest, UUID readerId) {
        return pullRequestShareMapper.toPreviewResponse(
                toPullRequestResponse(pullRequest, share.getOrganizationId(), readerId), renderSummary(share));
    }

    /**
     * Maps a pull request read from GitHub for the share preview, with the
     * ticket of its key when the reader can open it.
     *
     * @param pullRequest    the pull request as GitHub returned it
     * @param organizationId the organization the pull request is shown in; only its tickets link
     * @param readerId       ID of the user previewing
     * @return the response
     */
    public GithubPullRequestResponse toPullRequestResponse(GithubPullRequestPayload pullRequest,
                                                           UUID organizationId, UUID readerId) {
        String ticketKey = pullRequestShareMapper.toTicketKey(pullRequest);
        ChatLinkedTicketResponse linkedTicket = ticketKey == null
                ? null
                : loadLinkedTickets(readerId, organizationId, List.of(ticketKey)).get(ticketKey);
        return pullRequestShareMapper.toPullRequestResponse(pullRequest, linkedTicket);
    }

    /**
     * Resolves upper-case ticket keys to the tickets the reader can open in
     * the organization, in one query.
     *
     * @param readerId       ID of the reader; null resolves nothing
     * @param organizationId the organization whose boards the keys are looked up on
     * @param ticketKeys     upper-case keys, as stored on shares; nulls are skipped
     * @return the linked tickets by key; a key the reader cannot open is absent
     */
    public Map<String, ChatLinkedTicketResponse> loadLinkedTickets(UUID readerId, UUID organizationId,
                                                                   Collection<String> ticketKeys) {
        if (readerId == null) {
            return Map.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String ticketKey : ticketKeys) {
            if (ticketKey != null) {
                keys.add(ticketKey);
            }
        }
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, ChatLinkedTicketResponse> linked = new HashMap<>();
        for (Ticket ticket : ticketQueryService.getAccessibleTicketsByKeys(readerId, organizationId, keys)) {
            linked.put(ticket.getKey(), pullRequestShareMapper.toLinkedTicketResponse(ticket.getKey(), ticket));
        }
        return linked;
    }

    /**
     * Applies the edit's related tickets to the share's managed rows, keeping
     * the row of a kept ticket; see {@link #setUpdateRelations}.
     */
    private void setUpdateTickets(PullRequestShare share, List<UUID> relatedTicketIds, Map<UUID, Ticket> added) {
        Map<UUID, PullRequestShareTicket> stored = new HashMap<>();
        for (PullRequestShareTicket row : share.getRelatedTickets()) {
            stored.put(row.getTicketId(), row);
        }
        List<PullRequestShareTicket> updated = new ArrayList<>();
        for (int position = 0; position < relatedTicketIds.size(); position++) {
            UUID ticketId = relatedTicketIds.get(position);
            PullRequestShareTicket row = stored.get(ticketId);
            if (row != null) {
                row.setPosition(position);
            } else {
                row = pullRequestShareMapper.toTicketEntity(added.get(ticketId), position);
                row.setShare(share);
            }
            updated.add(row);
        }
        // The managed collection is mutated, never replaced, so orphan removal
        // sees the tickets that were left out.
        share.getRelatedTickets().clear();
        share.getRelatedTickets().addAll(updated);
    }

    /**
     * Maps the share's related tickets in display order, each with whether
     * the reader can open it.
     */
    private List<PullRequestShareTicketResponse> toTicketResponses(PullRequestShare share,
                                                                   Set<UUID> openableTicketIds) {
        return share.getRelatedTickets().stream()
                .map(row -> pullRequestShareMapper.toTicketResponse(row,
                        openableTicketIds.contains(row.getTicketId())))
                .toList();
    }

    /**
     * Returns the IDs among the shares' related tickets that the reader can
     * open: live tickets on the organization's boards the reader is a member
     * of. One query for all shares; none for a null reader or when no share
     * has a related ticket.
     */
    private Set<UUID> loadOpenableTicketIds(UUID readerId, UUID organizationId, List<PullRequestShare> shares) {
        if (readerId == null) {
            return Set.of();
        }
        Set<UUID> ticketIds = new LinkedHashSet<>();
        for (PullRequestShare share : shares) {
            for (PullRequestShareTicket row : share.getRelatedTickets()) {
                ticketIds.add(row.getTicketId());
            }
        }
        if (ticketIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> openable = new HashSet<>();
        for (Ticket ticket : ticketQueryService.getAccessibleTickets(readerId, organizationId, new ArrayList<>(ticketIds))) {
            openable.add(ticket.getId());
        }
        return openable;
    }

    /**
     * Renders a share's plain text in the team's format, the same for a
     * saved share and a preview.
     */
    private String renderSummary(PullRequestShare share) {
        return pullRequestShareSummaryRenderer.render(share.getTicketKey(), share.getTitle(),
                pullRequestShareMapper.toShareUrl(share), share.getStatus(), share.getNote());
    }
}
