package com.korur.oba.apps.kazan.ticket.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.dto.request.AllTicketQueryRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketQueryRequest;
import com.korur.oba.apps.kazan.ticket.dto.response.AllTicketListResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeLeaderboardResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeSprintResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeStaleBacklogResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.HomeSummaryResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.apps.kazan.ticket.dto.response.TicketListResponse;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.HomeLeaderboardPeriod;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for tickets.
 */
public interface TicketQueryService {

    /**
     * Lists tickets for a board with optional filters.
     */
    List<TicketListResponse> listTickets(User user, UUID organizationId, UUID boardId, TicketQueryRequest queryRequest);

    /**
     * Retrieves ticket details by board and id.
     */
    TicketDetailResponse getTicket(User user, UUID organizationId, UUID boardId, UUID ticketId);

    /**
     * Loads a live ticket by id, from any board of any organization, sub-tickets included.
     *
     * Performs no access check and no organization filter: the caller reads the organization
     * from the ticket's board and checks the user's access itself.
     *
     * @param ticketId the ticket
     * @return the ticket
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException NOT_TICKET_FOUND
     *         when no live ticket has the id
     */
    Ticket getEntityById(UUID ticketId);

    /**
     * Counts the total number of tickets in a board.
     * Used by other domains for key generation.
     *
     * @param boardId the board identifier
     * @return the number of tickets in the board
     */
    long countTicketsByBoardId(UUID boardId);

    /**
     * Lists all tickets across all boards where the user is a member.
     *
     * @param userId         the authenticated user's identifier
     * @param organizationId the organization of the request; only its boards are searched
     * @param queryRequest   the query request containing filter criteria
     * @return list of tickets matching the criteria
     */
    List<AllTicketListResponse> listAllTickets(UUID userId, UUID organizationId, AllTicketQueryRequest queryRequest);

    /**
     * Resolves a single ticket by its exact key, for any ticket including
     * sub-tickets, scoped to the boards where the user is a member.
     *
     * @param userId         the authenticated user's identifier
     * @param organizationId the organization of the request; only its boards are searched
     * @param key            the exact ticket key to resolve
     * @return the resolved ticket
     */
    AllTicketListResponse getTicketByKey(UUID userId, UUID organizationId, String key);

    /**
     * Counts active (non-DONE) tickets assigned to a specific user on a board.
     * Used by the assignment algorithm for workload balancing.
     *
     * @param boardId the board identifier
     * @param userId  the user identifier
     * @return the number of active tickets assigned to the user on the board
     */
    long countActiveTicketsByBoardIdAndAssigneeId(UUID boardId, UUID userId);

    /**
     * Returns the requested tickets that the user may read: non-deleted tickets,
     * sub-tickets included, on boards where the user is a member of any role.
     *
     * An ID that is absent from the result belongs to a deleted ticket or to a board
     * the user is not a member of; the method never throws for it, so a caller that
     * needs every ID decides itself whether a missing one is an error.
     *
     * @param userId         the user whose board memberships scope the lookup
     * @param organizationId the organization whose boards are searched; a ticket of
     *                       another organization is absent from the result
     * @param ticketIds      the ticket IDs to resolve; null or empty returns an empty list
     * @return the accessible tickets among the requested IDs, in no particular order
     */
    List<Ticket> getAccessibleTickets(UUID userId, UUID organizationId, List<UUID> ticketIds);

    /**
     * Returns the tickets with the requested exact keys that the user may read:
     * non-deleted tickets, sub-tickets included, on boards where the user is a member
     * of any role. Keys are compared exactly, case included.
     *
     * A key that is absent from the result belongs to no live ticket or to a board the
     * user is not a member of; the method never throws for it, so text that only looks
     * like a ticket key stays plain text for the caller.
     *
     * @param userId         the user whose board memberships scope the lookup
     * @param organizationId the organization whose boards are searched; a ticket of
     *                       another organization is absent from the result
     * @param keys           the ticket keys to resolve; null or empty returns an empty list
     * @return the accessible tickets among the requested keys, at most one per key, in no
     *         particular order
     */
    List<Ticket> getAccessibleTicketsByKeys(UUID userId, UUID organizationId, Collection<String> keys);

    /**
     * Lists the tickets the user is actively working on: assigned to the user,
     * status IN_PROGRESS, on boards where the user is a member, sub-tickets included,
     * ordered by key.
     *
     * @param userId         the user whose assigned tickets are listed
     * @param organizationId the organization of the request; only its boards are searched
     * @param limit          the maximum number of tickets to return; must be at least 1
     * @return at most limit tickets ordered by key
     */
    List<AllTicketListResponse> listActiveAssignedTickets(UUID userId, UUID organizationId, int limit);

    /**
     * Counts the open work on the boards where the user is a member: the INCIDENT tickets
     * that are not DONE, and the tickets that are not DONE in the ACTIVE sprints of those
     * boards. Sub-tickets are left out, and a sprint shared with a board the user is not a
     * member of contributes only the tickets on the user's boards.
     *
     * @param userId         the user whose board memberships scope the counts
     * @param organizationId the organization of the request; only its boards are searched
     * @return both counts, zero when the user is a member of no board
     */
    HomeSummaryResponse getHomeSummary(UUID userId, UUID organizationId);

    /**
     * Lists, per assignee, the tickets assigned to that user that are DONE in the ACTIVE
     * sprints of the boards where the reader is a member, sub-tickets included, ordered by
     * key. A ticket on a board the reader is not a member of is left out, even when its
     * sprint is shared with one of the reader's boards. One query covers every assignee.
     *
     * @param readerId       the user whose board memberships scope the tickets
     * @param organizationId the organization of the request; only its boards are searched
     * @param assigneeIds    the users whose finished tickets are listed
     * @return the tickets by assignee ID; an assignee without tickets has no entry, and
     *         the map is empty when assigneeIds is empty or the reader has no board or no
     *         active sprint
     */
    Map<UUID, List<Ticket>> getDoneActiveSprintTicketsByAssignees(UUID readerId, UUID organizationId,
                                                           Collection<UUID> assigneeIds);

    /**
     * Lists the stale backlog tickets on the boards where the user is a member: in a
     * backlog and in no sprint, not DONE, sub-tickets included, and without an edit or a
     * live comment for {@link com.korur.oba.apps.kazan.ticket.constant.HomeDashboardConstant#STALE_BACKLOG_DAYS}
     * days. The least recently active come first, capped at
     * {@link com.korur.oba.apps.kazan.ticket.constant.HomeDashboardConstant#STALE_BACKLOG_LIMIT};
     * the total counts them all. A ticket may be deleted by the user when the user may
     * contribute to its board.
     *
     * @param userId         the user whose board memberships scope the tickets
     * @param organizationId the organization of the request; only its boards are searched
     * @return the capped list and the total; empty with total zero when the user is a
     *         member of no board
     */
    HomeStaleBacklogResponse getStaleBacklog(UUID userId, UUID organizationId);

    /**
     * Ranks the assignees of the tickets finished on the boards where the user is a
     * member by size points: each ticket's points from
     * {@link com.korur.oba.apps.kazan.ticket.constant.HomeDashboardConstant#MISSION_RANK_POINTS},
     * split evenly between its assignees and rounded per user to one decimal. A ticket
     * without assignees counts for nobody.
     *
     * SPRINT counts the tickets of {@link #getDoneActiveSprintTicketsByAssignees} for the
     * members of the user's boards, so the leaderboard and the daily update cards agree.
     * LAST_30_DAYS counts the tickets that reached DONE in the last 30 days. SPRINT without
     * an ACTIVE sprint on the user's boards falls back to LAST_30_DAYS, and the response
     * names the period it counted.
     *
     * @param userId         the user whose board memberships scope the tickets
     * @param organizationId the organization of the request; only its boards are searched
     * @param period         the period asked for
     * @return every credited user, most points first, then most tickets, then last name;
     *         no entries when the user is a member of no board
     */
    HomeLeaderboardResponse getLeaderboard(UUID userId, UUID organizationId, HomeLeaderboardPeriod period);

    /**
     * Resolves the home page's focus board and its ACTIVE sprint, with the sprint's
     * burndown. The focus board is the requested board when it is one of the user's boards
     * with an ACTIVE sprint; otherwise the user's top-level board with an ACTIVE sprint,
     * first by name; otherwise any of the user's boards with an ACTIVE sprint, first by
     * name. A board holding two ACTIVE sprints shows the one that started first.
     *
     * The burndown counts the sprint's list roots on the user's boards: per day of the
     * sprint on the Istanbul calendar of the daily updates, the tickets not yet DONE at the
     * end of that day, and an ideal line falling evenly from the total to zero.
     *
     * @param userId         the user whose board memberships scope the boards and tickets
     * @param organizationId the organization of the request; only its boards are searched
     * @param boardId        the requested focus board; null, or a board that does not qualify,
     *                       falls back to the rule above instead of failing
     * @return the focus sprint; board and sprint fields null when none of the user's
     *         boards runs an ACTIVE sprint
     */
    HomeSprintResponse getHomeSprint(UUID userId, UUID organizationId, UUID boardId);
}
