package com.korur.oba.apps.kazan.dailyupdate.mapper;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateCreateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateUpdateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateFinishedTicketResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdatePreviewResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateTicketResponse;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.apps.kazan.dailyupdate.helper.DailyUpdateSummaryRenderer;
import com.korur.oba.apps.kazan.dailyupdate.validator.DailyUpdateValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.service.TicketQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Resolves the tickets of daily update lines, applies lines to updates, and
 * builds responses with the reader's canOpen flags and the summary text.
 *
 * Lines are matched by ticket ID. A kept line is changed in place and keeps
 * its snapshot without an access check, so a line on a board the author has
 * left survives an edit; only an added ticket must be reachable by the author.
 * A line whose ticket row was removed has no ticket ID, so no request can name
 * it; it is kept unchanged at its place among the lines, because it records
 * what the author reported and the author has no way to restate it.
 * canOpen is computed with one ticket query per call, however many updates and
 * lines are mapped, and is true only for a live ticket on a board the reader
 * is a member of: the same rule the ticket detail endpoint enforces. A line's
 * Mission Rank and priority come from that same query, so a reader who cannot
 * open the ticket gets neither. The author's finished tickets are read live
 * for the reader too, with one more ticket query per call, and cover only the
 * reader's boards.
 *
 * Tickets are always resolved in the update's organization, and only while
 * that organization is active and the reader is an active member of it: the
 * daily update paths pass no organization check of the interceptor, and a
 * board's tickets are that organization's data. An update without an organization shows no ticket as
 * openable and no finished tickets. Mapping several updates runs the two
 * ticket queries once per organization among them.
 */
@Component
@RequiredArgsConstructor
public class DailyUpdateMapperHelper {

    private final TicketQueryService ticketQueryService;
    private final DailyUpdateMapper dailyUpdateMapper;
    private final DailyUpdateSummaryRenderer dailyUpdateSummaryRenderer;
    private final DailyUpdateValidator dailyUpdateValidator;
    private final OrganizationMemberQueryService organizationMemberQueryService;

    /**
     * Returns the organization whose tickets the reader may see on an update:
     * the update's organization while it is active and the reader is an active
     * member of it, else null, which resolves no ticket.
     *
     * @param organizationId the update's organization, nullable
     * @param readerId       ID of the reader
     * @return organizationId, or null when it is null, disabled, or the reader is not an active member of it
     */
    public UUID resolveTicketOrganizationId(UUID organizationId, UUID readerId) {
        if (organizationId == null || !isActiveMember(organizationId, readerId)) {
            return null;
        }
        return organizationId;
    }

    /**
     * Loads the tickets the user may put on an update, indexed by ID.
     *
     * @param userId         ID of the user writing the update
     * @param organizationId the organization whose boards the tickets must be on, from
     *                       the checked posting organization or resolveTicketOrganizationId;
     *                       null resolves no ticket
     * @param ticketIds      IDs of the tickets to resolve
     * @return the tickets by ID; empty when ticketIds is empty
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_TICKET_FOUND when a ticket is deleted, on a board of another organization,
     *         or on a board the user is not a member of
     */
    public Map<UUID, Ticket> resolveAccessibleTickets(UUID userId, UUID organizationId, Collection<UUID> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        List<Ticket> tickets = organizationId == null
                ? List.of()
                : ticketQueryService.getAccessibleTickets(userId, organizationId, new ArrayList<>(ticketIds));
        dailyUpdateValidator.ensureTicketsAccessible(ticketIds, tickets);
        Map<UUID, Ticket> ticketsById = new HashMap<>();
        for (Ticket ticket : tickets) {
            ticketsById.put(ticket.getId(), ticket);
        }
        return ticketsById;
    }

    /**
     * Builds new, unattached lines in request order, each with a snapshot of
     * its ticket. Used by the create preview, which saves nothing.
     *
     * @param lines       ticket lines of the request
     * @param ticketsById tickets of every line, from resolveAccessibleTickets
     * @return the lines, positions 0 to n-1
     */
    public List<DailyUpdateTicket> buildItems(List<DailyUpdateTicketRequest> lines, Map<UUID, Ticket> ticketsById) {
        List<DailyUpdateTicket> items = new ArrayList<>();
        for (int position = 0; position < lines.size(); position++) {
            DailyUpdateTicketRequest line = lines.get(position);
            items.add(dailyUpdateMapper.toItemEntity(line, ticketsById.get(line.ticketId()), position));
        }
        return items;
    }

    /**
     * Sets the author and the lines of a new update.
     *
     * @param request     the create request
     * @param dailyUpdate the new update, from DailyUpdateMapper.toEntity
     * @param author      the current user
     * @param ticketsById tickets of every line, from resolveAccessibleTickets
     */
    public void setCreateRelations(DailyUpdateCreateRequest request, DailyUpdate dailyUpdate, User author,
                                   Map<UUID, Ticket> ticketsById) {
        dailyUpdate.setAuthor(author);
        for (DailyUpdateTicket item : buildItems(request.tickets(), ticketsById)) {
            item.setDailyUpdate(dailyUpdate);
            dailyUpdate.getItems().add(item);
        }
    }

    /**
     * Lists the request's tickets that are not yet lines of the update, in
     * request order. These are the tickets an edit adds and must resolve.
     *
     * @param dailyUpdate the stored update
     * @param lines       ticket lines of the edit request
     * @return IDs of the added tickets
     */
    public Set<UUID> findAddedTicketIds(DailyUpdate dailyUpdate, List<DailyUpdateTicketRequest> lines) {
        Map<UUID, DailyUpdateTicket> stored = indexByTicketId(dailyUpdate);
        Set<UUID> added = new LinkedHashSet<>();
        for (DailyUpdateTicketRequest line : lines) {
            if (!stored.containsKey(line.ticketId())) {
                added.add(line.ticketId());
            }
        }
        return added;
    }

    /**
     * Lists the kept lines whose percentage the edit changes, in request order.
     * Call it before setUpdateRelations, which overwrites the stored percentages.
     *
     * @param dailyUpdate the stored update
     * @param lines       ticket lines of the edit request
     * @return IDs of the tickets of kept lines with a new percentage
     */
    public Set<UUID> findChangedPercentageTicketIds(DailyUpdate dailyUpdate, List<DailyUpdateTicketRequest> lines) {
        Map<UUID, DailyUpdateTicket> stored = indexByTicketId(dailyUpdate);
        Set<UUID> changed = new LinkedHashSet<>();
        for (DailyUpdateTicketRequest line : lines) {
            DailyUpdateTicket item = stored.get(line.ticketId());
            if (item != null && !item.getPercentage().equals(line.percentage())) {
                changed.add(line.ticketId());
            }
        }
        return changed;
    }

    /**
     * Replaces the update's lines with the edit request's lines. A kept line
     * is changed in place (percentage, note, position) and keeps its snapshot
     * and progressSynced; an added ticket gets a new line with a fresh
     * snapshot; a stored line missing from the request is removed by orphan
     * removal. A line without a ticket keeps its percentage, note and snapshot
     * and stays at its index among the lines. The same entity is kept for the
     * same ticket, because removing and re-inserting it would break the unique
     * key on (update, ticket): Hibernate flushes inserts before orphan deletes.
     *
     * Focus and questions are applied by DailyUpdateMapper.updateEntity.
     *
     * @param request          the edit request
     * @param dailyUpdate      the stored update
     * @param addedTicketsById tickets of the added lines, from resolveAccessibleTickets
     *                         over findAddedTicketIds
     */
    public void setUpdateRelations(DailyUpdateUpdateRequest request, DailyUpdate dailyUpdate,
                                   Map<UUID, Ticket> addedTicketsById) {
        List<DailyUpdateTicketRequest> lines = request.tickets();
        dailyUpdateValidator.ensureTicketsAccessible(findAddedTicketIds(dailyUpdate, lines), addedTicketsById.values());
        Map<UUID, DailyUpdateTicket> stored = indexByTicketId(dailyUpdate);
        List<DailyUpdateTicket> items = new ArrayList<>();
        for (int position = 0; position < lines.size(); position++) {
            DailyUpdateTicketRequest line = lines.get(position);
            DailyUpdateTicket item = stored.get(line.ticketId());
            if (item != null) {
                dailyUpdateMapper.updateItemEntity(item, line, position);
            } else {
                item = dailyUpdateMapper.toItemEntity(line, addedTicketsById.get(line.ticketId()), position);
                item.setDailyUpdate(dailyUpdate);
            }
            items.add(item);
        }
        List<DailyUpdateTicket> merged = insertTicketlessLines(dailyUpdate.getItems(), items);
        // The managed collection is mutated, never replaced, so orphan removal
        // sees the lines that were left out.
        dailyUpdate.getItems().clear();
        dailyUpdate.getItems().addAll(merged);
    }

    /**
     * Builds the lines an edit would produce without touching the stored
     * update: kept lines are copied with the edit applied, added tickets get a
     * new line, and a line without a ticket is copied unchanged at its index.
     * Used by the edit preview, which saves nothing.
     *
     * @param dailyUpdate      the stored update
     * @param lines            ticket lines of the edit request
     * @param addedTicketsById tickets of the added lines, from resolveAccessibleTickets
     *                         over findAddedTicketIds
     * @return detached lines in request order, progressSynced false
     */
    public List<DailyUpdateTicket> buildEditPreviewItems(DailyUpdate dailyUpdate, List<DailyUpdateTicketRequest> lines,
                                                         Map<UUID, Ticket> addedTicketsById) {
        dailyUpdateValidator.ensureTicketsAccessible(findAddedTicketIds(dailyUpdate, lines), addedTicketsById.values());
        Map<UUID, DailyUpdateTicket> stored = indexByTicketId(dailyUpdate);
        List<DailyUpdateTicket> items = new ArrayList<>();
        for (int position = 0; position < lines.size(); position++) {
            DailyUpdateTicketRequest line = lines.get(position);
            DailyUpdateTicket item = stored.get(line.ticketId());
            items.add(item != null
                    ? dailyUpdateMapper.toPreviewItem(item, line, position)
                    : dailyUpdateMapper.toItemEntity(line, addedTicketsById.get(line.ticketId()), position));
        }
        List<DailyUpdateTicket> storedLines = new ArrayList<>();
        for (DailyUpdateTicket item : dailyUpdate.getItems()) {
            storedLines.add(item.getTicketId() == null ? copyTicketlessLine(item) : item);
        }
        return insertTicketlessLines(storedLines, items);
    }

    /**
     * Maps updates to responses for one reader, with one canOpen query for all
     * their lines and one finished-tickets query for all their authors.
     *
     * @param dailyUpdates the updates, in the order to return
     * @param readerId     ID of the user reading them
     * @return the responses in the same order
     */
    public List<DailyUpdateResponse> toResponses(List<DailyUpdate> dailyUpdates, UUID readerId) {
        if (dailyUpdates.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<DailyUpdateTicket>> itemsByOrganization = new HashMap<>();
        Map<UUID, Set<UUID>> authorIdsByOrganization = new HashMap<>();
        Map<UUID, Boolean> readerIsMember = new HashMap<>();
        for (DailyUpdate dailyUpdate : dailyUpdates) {
            UUID organizationId = dailyUpdate.getOrganizationId();
            if (organizationId == null
                    || !readerIsMember.computeIfAbsent(organizationId, id -> isActiveMember(id, readerId))) {
                continue;
            }
            itemsByOrganization.computeIfAbsent(organizationId, id -> new ArrayList<>())
                    .addAll(dailyUpdate.getItems());
            authorIdsByOrganization.computeIfAbsent(organizationId, id -> new LinkedHashSet<>())
                    .add(dailyUpdate.getAuthor().getId());
        }

        Map<UUID, Map<UUID, Ticket>> openableByOrganization = new HashMap<>();
        Map<UUID, Map<UUID, List<DailyUpdateFinishedTicketResponse>>> finishedByOrganization = new HashMap<>();
        for (Map.Entry<UUID, List<DailyUpdateTicket>> entry : itemsByOrganization.entrySet()) {
            UUID organizationId = entry.getKey();
            openableByOrganization.put(organizationId, loadOpenableTickets(readerId, organizationId, entry.getValue()));
            finishedByOrganization.put(organizationId,
                    loadFinishedTickets(readerId, organizationId, authorIdsByOrganization.get(organizationId)));
        }

        return dailyUpdates.stream()
                .map((DailyUpdate dailyUpdate) -> {
                    UUID organizationId = dailyUpdate.getOrganizationId();
                    Map<UUID, Ticket> openableTickets = organizationId == null
                            ? Map.of()
                            : openableByOrganization.getOrDefault(organizationId, Map.of());
                    List<DailyUpdateFinishedTicketResponse> finishedTickets = organizationId == null
                            ? List.of()
                            : finishedByOrganization.getOrDefault(organizationId, Map.of())
                                    .getOrDefault(dailyUpdate.getAuthor().getId(), List.of());
                    return dailyUpdateMapper.toResponse(dailyUpdate,
                            toTicketResponses(dailyUpdate.getItems(), openableTickets),
                            finishedTickets,
                            dailyUpdateSummaryRenderer.render(dailyUpdate.getItems(),
                                    dailyUpdate.getFocus(), dailyUpdate.getQuestions()));
                })
                .toList();
    }

    /**
     * Maps a page of updates to a page of responses for one reader, keeping
     * the paging metadata.
     *
     * @param page     the page of updates
     * @param readerId ID of the user reading them
     * @return the page of responses
     */
    public Page<DailyUpdateResponse> toResponsePage(Page<DailyUpdate> page, UUID readerId) {
        return new PageImpl<>(toResponses(page.getContent(), readerId), page.getPageable(), page.getTotalElements());
    }

    /**
     * Maps one update to a response for one reader.
     *
     * @param dailyUpdate the update
     * @param readerId    ID of the user reading it
     * @return the response
     */
    public DailyUpdateResponse toResponse(DailyUpdate dailyUpdate, UUID readerId) {
        return toResponses(List.of(dailyUpdate), readerId).getFirst();
    }

    /**
     * Maps the lines a request would produce, with the text they would render.
     *
     * @param items     lines from buildItems or buildEditPreviewItems, progressSynced
     *                  already set to whether this request would sync each line
     * @param focus          focus text of the request, nullable
     * @param questions      questions text of the request, nullable
     * @param organizationId the organization whose tickets the preview shows, from the checked
     *                       posting organization or resolveTicketOrganizationId; null shows none
     * @param readerId       ID of the user previewing, who is the author
     * @return the preview response
     */
    public DailyUpdatePreviewResponse toPreviewResponse(List<DailyUpdateTicket> items, String focus, String questions,
                                                        UUID organizationId, UUID readerId) {
        Map<UUID, Ticket> openableTickets = organizationId == null
                ? Map.of()
                : loadOpenableTickets(readerId, organizationId, items);
        List<DailyUpdateFinishedTicketResponse> finishedTickets = organizationId == null
                ? List.of()
                : loadFinishedTickets(readerId, organizationId, List.of(readerId)).getOrDefault(readerId, List.of());
        return dailyUpdateMapper.toPreviewResponse(
                toTicketResponses(items, openableTickets),
                finishedTickets,
                focus,
                questions,
                dailyUpdateSummaryRenderer.render(items, focus, questions));
    }

    private List<DailyUpdateTicketResponse> toTicketResponses(List<DailyUpdateTicket> items,
                                                              Map<UUID, Ticket> openableTickets) {
        return items.stream()
                .map((DailyUpdateTicket item) -> {
                    Ticket ticket = item.getTicketId() == null ? null : openableTickets.get(item.getTicketId());
                    return dailyUpdateMapper.toTicketResponse(item, ticket != null,
                            ticket == null ? null : ticket.getMissionRank(),
                            ticket == null ? null : ticket.getPriority());
                })
                .toList();
    }

    /**
     * Returns, per author, the tickets the author finished in the active sprints
     * of the reader's boards in the organization, mapped. One query for all
     * authors; an author without such tickets has no entry.
     */
    private Map<UUID, List<DailyUpdateFinishedTicketResponse>> loadFinishedTickets(UUID readerId, UUID organizationId,
                                                                                   Collection<UUID> authorIds) {
        Map<UUID, List<DailyUpdateFinishedTicketResponse>> finishedByAuthor = new HashMap<>();
        for (Map.Entry<UUID, List<Ticket>> entry : ticketQueryService
                .getDoneActiveSprintTicketsByAssignees(readerId, organizationId, authorIds).entrySet()) {
            finishedByAuthor.put(entry.getKey(), dailyUpdateMapper.toFinishedTicketResponses(entry.getValue()));
        }
        return finishedByAuthor;
    }

    /**
     * Returns the lines' tickets that the reader can open, by ID: live tickets
     * on the organization's boards the reader is a member of. One query; none
     * when no line has a ticket.
     */
    private Map<UUID, Ticket> loadOpenableTickets(UUID readerId, UUID organizationId,
                                                  Collection<DailyUpdateTicket> items) {
        Set<UUID> ticketIds = new LinkedHashSet<>();
        for (DailyUpdateTicket item : items) {
            if (item.getTicketId() != null) {
                ticketIds.add(item.getTicketId());
            }
        }
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Ticket> openable = new HashMap<>();
        for (Ticket ticket : ticketQueryService.getAccessibleTickets(readerId, organizationId, new ArrayList<>(ticketIds))) {
            openable.put(ticket.getId(), ticket);
        }
        return openable;
    }

    /**
     * Tells whether the user holds an ACTIVE membership, of any role, in an
     * ACTIVE organization. The daily update paths pass no organization check of
     * the interceptor, so a disabled organization is refused here: its boards
     * grant no access, and its tickets must not show on an update either.
     */
    private boolean isActiveMember(UUID organizationId, UUID userId) {
        return organizationMemberQueryService.findMember(organizationId, userId)
                .map((OrganizationMember member) -> member.getStatus() == OrganizationMemberStatus.ACTIVE
                        && member.getOrganization().getStatus() == OrganizationStatus.ACTIVE)
                .orElse(false);
    }

    /**
     * Returns the request's lines with every stored line that has no ticket
     * inserted at its former index, clamped to the end, and renumbers the
     * positions. Stored lines that have a ticket are ignored here: the request
     * lines already stand for them.
     *
     * @param storedItems  the stored lines in position order, or copies of them
     * @param requestItems the lines built from the request, in request order
     * @return the merged lines, positions 0 to n-1
     */
    private static List<DailyUpdateTicket> insertTicketlessLines(List<DailyUpdateTicket> storedItems,
                                                                 List<DailyUpdateTicket> requestItems) {
        List<DailyUpdateTicket> merged = new ArrayList<>(requestItems);
        for (int index = 0; index < storedItems.size(); index++) {
            DailyUpdateTicket stored = storedItems.get(index);
            if (stored.getTicketId() == null) {
                merged.add(Math.min(index, merged.size()), stored);
            }
        }
        for (int position = 0; position < merged.size(); position++) {
            merged.get(position).setPosition(position);
        }
        return merged;
    }

    /**
     * Copies a line without a ticket for a preview, so renumbering the copy
     * leaves the stored line untouched. progressSynced starts false, because
     * in a preview it tells whether this request would sync, and a line
     * without a ticket never syncs.
     */
    private static DailyUpdateTicket copyTicketlessLine(DailyUpdateTicket item) {
        return DailyUpdateTicket.builder()
                .boardId(item.getBoardId())
                .boardName(item.getBoardName())
                .ticketKey(item.getTicketKey())
                .ticketTitle(item.getTicketTitle())
                .percentage(item.getPercentage())
                .note(item.getNote())
                .position(item.getPosition())
                .build();
    }

    /**
     * Indexes the stored lines by ticket ID. A line whose ticket row was
     * removed has no ticket ID and is left out; insertTicketlessLines keeps it.
     */
    private static Map<UUID, DailyUpdateTicket> indexByTicketId(DailyUpdate dailyUpdate) {
        Map<UUID, DailyUpdateTicket> stored = new HashMap<>();
        for (DailyUpdateTicket item : dailyUpdate.getItems()) {
            if (item.getTicketId() != null) {
                stored.put(item.getTicketId(), item);
            }
        }
        return stored;
    }
}
