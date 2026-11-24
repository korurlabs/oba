package com.korur.oba.apps.kazan.ticket.specification;

// 1. Framework-level
import org.springframework.data.jpa.domain.Specification;

// 2. Third-party
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// 3. Java SDK
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.ticket.dto.request.AllTicketQueryRequest;
import com.korur.oba.apps.kazan.ticket.dto.request.TicketQueryRequest;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kazan.ticket.entity.TicketLabel;
import com.korur.oba.apps.kazan.ticket.entity.TicketReviewer;
import com.korur.oba.apps.kazan.ticket.enums.TicketPriority;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.enums.TicketType;

/**
 * JPA Specification builder for Ticket entity queries.
 * <p>
 * Provides composable query predicates for filtering tickets based on various
 * criteria.
 * Uses noOp() with cb.conjunction() for null parameters to ensure correct AND
 * chaining.
 */
public class TicketSpecification {

    /**
     * How many levels of sub-tickets a keyword search looks through below a listed ticket.
     * The board only creates one level, but older data and the API can hold deeper chains.
     */
    private static final int SEARCH_SUB_TICKET_DEPTH = 3;

    private TicketSpecification() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a no-op specification that matches all records.
     * Uses cb.conjunction() which translates to SQL TRUE (or 1=1).
     *
     * WARNING: Do NOT use Specification.anyOf() here — it matches NOTHING!
     */
    private static Specification<Ticket> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    /**
     * Filters by ticket statuses.
     * Returns no-op if {@code statuses} is null or empty.
     */
    private static Specification<Ticket> byStatuses(List<TicketStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /**
     * Filters by ticket types.
     * Returns no-op if {@code types} is null or empty.
     *
     * TASK is an input alias that is never stored, and the database enum type rejects it as a
     * bound value. It is removed before the query, so a filter of only TASK matches no rows
     * instead of failing.
     */
    @SuppressWarnings("deprecation")
    private static Specification<Ticket> byTypes(List<TicketType> types) {
        if (types == null || types.isEmpty()) {
            return noOp();
        }
        List<TicketType> storedTypes = types.stream()
                .filter(type -> type != TicketType.TASK)
                .toList();
        if (storedTypes.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("type").in(storedTypes);
    }

    /**
     * Filters by ticket priorities.
     * Returns no-op if {@code priorities} is null or empty.
     */
    private static Specification<Ticket> byPriorities(List<TicketPriority> priorities) {
        if (priorities == null || priorities.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get("priority").in(priorities);
    }

    /**
     * Filters by sprint ID.
     * Returns no-op if {@code sprintId} is null.
     */
    private static Specification<Ticket> bySprintId(UUID sprintId) {
        if (sprintId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get("sprint").get("id"), sprintId);
    }

    /**
     * Filters by backlog IDs.
     * Returns no-op if {@code backlogIds} is null or empty.
     */
    private static Specification<Ticket> byBacklogIds(List<UUID> backlogIds) {
        if (backlogIds == null || backlogIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get("backlog").get("id").in(backlogIds);
    }

    /**
     * Filters by project ID.
     * Returns no-op if {@code projectId} is null.
     */
    private static Specification<Ticket> byProjectId(UUID projectId) {
        if (projectId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    /**
     * Filters by tickets assigned to at least one of the given users.
     * Returns no-op if {@code assigneeIds} is null or empty.
     *
     * An EXISTS subquery on the ticket-assignee join entity, for the same reason as
     * byLabelIds: a join would list a ticket once per selected assignee.
     * Soft-deleted assignments and users never match.
     */
    private static Specification<Ticket> byAssignees(List<UUID> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> {
            Subquery<UUID> ticketAssigneeIds = query.subquery(UUID.class);
            Root<TicketAssignee> ticketAssignee = ticketAssigneeIds.from(TicketAssignee.class);
            ticketAssigneeIds.select(ticketAssignee.get("id"))
                    .where(cb.equal(ticketAssignee.get("ticket").get("id"), root.get("id")),
                            ticketAssignee.join("user").get("id").in(assigneeIds));
            return cb.exists(ticketAssigneeIds);
        };
    }

    /**
     * Filters by tickets that have at least one of the given users as reviewer.
     * Returns no-op if {@code reviewerIds} is null or empty.
     *
     * An EXISTS subquery on the ticket-reviewer join entity, for the same reason as
     * byLabelIds: a join would list a ticket once per selected reviewer.
     * Soft-deleted reviewer links and users never match.
     */
    private static Specification<Ticket> byReviewers(List<UUID> reviewerIds) {
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> {
            Subquery<UUID> ticketReviewerIds = query.subquery(UUID.class);
            Root<TicketReviewer> ticketReviewer = ticketReviewerIds.from(TicketReviewer.class);
            ticketReviewerIds.select(ticketReviewer.get("id"))
                    .where(cb.equal(ticketReviewer.get("ticket").get("id"), root.get("id")),
                            ticketReviewer.join("user").get("id").in(reviewerIds));
            return cb.exists(ticketReviewerIds);
        };
    }

    /**
     * Filters by tickets that one of the given users created.
     * Returns no-op if createdByIds is null or empty.
     *
     * A ticket created by a system actor has no user creator, so its null
     * created_by_id never matches the IN predicate.
     */
    private static Specification<Ticket> byCreatedByIds(List<UUID> createdByIds) {
        if (createdByIds == null || createdByIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> root.get("createdBy").get("id").in(createdByIds);
    }

    /**
     * Case-insensitive keyword search across ticket title and key.
     * Returns no-op if {@code keyword} is null or blank.
     *
     * A listed ticket also matches when one of the sub-tickets nested under it in the list
     * matches, so a sub-ticket can be found by searching even though the list holds only
     * its root. A sub-ticket counts as nested when it, and every ticket between it and the
     * listed one, is on one of the boards and in the same sprint (sprint list), in one of the
     * backlogs and no sprint (backlog list), or anywhere (all-tickets list). The match is an
     * EXISTS subquery per level, so a ticket with several matching sub-tickets is listed once.
     *
     * With a milestone filter every ticket is listed on its own row, sub-tickets included, so
     * the search matches each row by its own text only.
     *
     * @param keyword     the search text; null or blank matches every ticket
     * @param boardIds    the boards a nested sub-ticket must be on
     * @param milestoneId the milestone filter; when set, sub-tickets are not searched through
     * @param sprintId    the sprint of a sprint list; null otherwise
     * @param backlogIds  the backlogs of a backlog list; null or empty otherwise
     */
    private static Specification<Ticket> searchSpecification(String keyword, List<UUID> boardIds,
                                                             UUID milestoneId, UUID sprintId,
                                                             List<UUID> backlogIds) {
        if (keyword == null || keyword.isBlank()) {
            return noOp();
        }
        String pattern = "%" + keyword.toLowerCase() + "%";
        int depth = milestoneId != null ? 0 : SEARCH_SUB_TICKET_DEPTH;
        return (root, query, cb) -> matchesSelfOrSubTicket(root, query, cb, pattern, depth,
                boardIds, sprintId, backlogIds);
    }

    /**
     * Matches a ticket whose title or key contains the pattern, or one with a nested
     * sub-ticket, down to {@code depth} levels, that does.
     */
    private static Predicate matchesSelfOrSubTicket(From<?, Ticket> ticket, CommonAbstractCriteria owner,
                                                    CriteriaBuilder cb, String pattern, int depth,
                                                    List<UUID> boardIds, UUID sprintId,
                                                    List<UUID> backlogIds) {
        Predicate self = cb.or(
                cb.like(cb.lower(ticket.get("title")), pattern),
                cb.like(cb.lower(ticket.get("key")), pattern));
        if (depth == 0 || boardIds == null || boardIds.isEmpty()) {
            return self;
        }
        Subquery<UUID> subTicketIds = owner.subquery(UUID.class);
        Root<Ticket> subTicket = subTicketIds.from(Ticket.class);
        subTicketIds.select(subTicket.get("id"))
                .where(cb.equal(subTicket.get("parentTicket").get("id"), ticket.get("id")),
                        subTicket.get("board").get("id").in(boardIds),
                        nestedPlacement(subTicket, cb, sprintId, backlogIds),
                        matchesSelfOrSubTicket(subTicket, subTicketIds, cb, pattern, depth - 1,
                                boardIds, sprintId, backlogIds));
        return cb.or(self, cb.exists(subTicketIds));
    }

    /**
     * The placement a sub-ticket needs to sit under its parent in the list: the list's sprint,
     * or no sprint and one of the list's backlogs. Any placement nests in the all-tickets list.
     */
    private static Predicate nestedPlacement(Root<Ticket> subTicket, CriteriaBuilder cb, UUID sprintId,
                                             List<UUID> backlogIds) {
        if (sprintId != null) {
            return cb.equal(subTicket.get("sprint").get("id"), sprintId);
        }
        if (backlogIds != null && !backlogIds.isEmpty()) {
            // A left join, as in byListRoots: a path to the sprint would join it inner and
            // drop every sub-ticket without one.
            Join<Ticket, Sprint> subTicketSprint = subTicket.join("sprint", JoinType.LEFT);
            return cb.and(cb.isNull(subTicketSprint.get("id")),
                    subTicket.get("backlog").get("id").in(backlogIds));
        }
        return cb.conjunction();
    }

    /**
     * Filters by milestone ID.
     * Returns no-op if {@code milestoneId} is null.
     */
    private static Specification<Ticket> byMilestoneId(UUID milestoneId) {
        if (milestoneId == null) {
            return noOp();
        }
        return (root, query, cb) -> cb.equal(root.get("milestone").get("id"), milestoneId);
    }

    /**
     * Filters by tickets that have no parent, unless a milestone filter is set:
     * sub-tickets attach to milestones individually and count towards their
     * progress, so a milestone's list includes them to match its counts.
     */
    private static Specification<Ticket> byOnlyParentTickets(UUID milestoneId) {
        if (milestoneId != null) {
            return noOp();
        }
        return (root, query, cb) -> cb.isNull(root.get("parentTicket"));
    }

    /**
     * Filters the tickets of a sprint or backlog list down to its roots: tickets whose
     * parent is null or is not placed in the same list. A sub-ticket planned apart from
     * its parent is a root, and one placed with its parent is nested under it. A
     * ticket's sprint wins over its backlog, so a parent in a sprint is never in a
     * backlog list. A milestone filter keeps every ticket: sub-tickets attach to
     * milestones individually and count towards their progress, so a milestone's list
     * includes them to match its counts.
     */
    private static Specification<Ticket> byListRoots(UUID milestoneId, UUID sprintId, List<UUID> backlogIds) {
        if (milestoneId != null) {
            return noOp();
        }
        if (sprintId != null) {
            return (root, query, cb) -> {
                Join<Ticket, Ticket> parent = root.join("parentTicket", JoinType.LEFT);
                Join<Ticket, Sprint> parentSprint = parent.join("sprint", JoinType.LEFT);
                return cb.or(
                        cb.isNull(parent.get("id")),
                        cb.isNull(parentSprint.get("id")),
                        cb.notEqual(parentSprint.get("id"), sprintId));
            };
        }
        if (backlogIds != null && !backlogIds.isEmpty()) {
            return (root, query, cb) -> {
                Join<Ticket, Ticket> parent = root.join("parentTicket", JoinType.LEFT);
                Join<Ticket, Sprint> parentSprint = parent.join("sprint", JoinType.LEFT);
                Join<Ticket, Backlog> parentBacklog = parent.join("backlog", JoinType.LEFT);
                return cb.or(
                        cb.isNull(parent.get("id")),
                        cb.isNotNull(parentSprint.get("id")),
                        cb.isNull(parentBacklog.get("id")),
                        cb.not(parentBacklog.get("id").in(backlogIds)));
            };
        }
        return (root, query, cb) -> cb.isNull(root.get("parentTicket"));
    }

    /**
     * Filters by an exact, case-sensitive ticket key match.
     * Returns a disjunction (SQL FALSE / match-nothing) if {@code key} is null or
     * blank, so a missing key resolves to no result rather than matching all tickets.
     */
    private static Specification<Ticket> byExactKey(String key) {
        if (key == null || key.isBlank()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("key"), key);
    }

    /**
     * Filters by exact ticket keys.
     * Returns a disjunction (SQL FALSE / match-nothing) if keys is null or empty,
     * so a lookup of no keys never widens to every ticket on the boards.
     */
    private static Specification<Ticket> byExactKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("key").in(keys);
    }

    /**
     * Filters by multiple board IDs.
     * Returns a disjunction (SQL FALSE / match-nothing) if boardIds is null or empty,
     * ensuring no tickets are returned when the caller has no board access.
     */
    private static Specification<Ticket> byBoardIds(List<UUID> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("board").get("id").in(boardIds);
    }

    /**
     * Filters by ticket IDs.
     * Returns a disjunction (SQL FALSE / match-nothing) if ticketIds is null or empty,
     * so a lookup of no tickets never widens to every ticket on the boards.
     */
    private static Specification<Ticket> byIds(List<UUID> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("id").in(ticketIds);
    }

    /**
     * Filters by tickets that carry at least one of the given labels.
     * Returns no-op if {@code labelIds} is null or empty.
     *
     * The match is an EXISTS subquery on the ticket-label join entity rather than a
     * join on the ticket query: a join yields one row per matching label, so a ticket
     * carrying two selected labels would be listed twice, and a DISTINCT to undo that
     * breaks PostgreSQL ordering when the caller sorts by a field outside the ticket.
     * Soft-deleted ticket-label links and labels never match.
     */
    private static Specification<Ticket> byLabelIds(List<UUID> labelIds) {
        if (labelIds == null || labelIds.isEmpty()) {
            return noOp();
        }
        return (root, query, cb) -> {
            Subquery<UUID> ticketLabelIds = query.subquery(UUID.class);
            Root<TicketLabel> ticketLabel = ticketLabelIds.from(TicketLabel.class);
            ticketLabelIds.select(ticketLabel.get("id"))
                    .where(cb.equal(ticketLabel.get("ticket").get("id"), root.get("id")),
                            ticketLabel.join("label").get("id").in(labelIds));
            return cb.exists(ticketLabelIds);
        };
    }

    /**
     * Filters by due date range using inclusive start and inclusive end.
     * Converts {@link LocalDate} to {@link java.time.Instant} at UTC midnight.
     * Returns no-op if both {@code start} and {@code end} are null.
     */
    private static Specification<Ticket> byDueDateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return noOp();
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"),
                        start.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (end != null) {
                predicates.add(cb.lessThan(root.get("dueDate"),
                        end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Builds a composite {@link Specification} for filtering {@link Ticket}
     * entities across multiple boards.
     *
     * @param boardIds the board IDs to scope tickets to
     * @param request  the query request containing filter criteria
     * @return combined specification with all applicable filters
     */
    public static Specification<Ticket> filterByBoards(List<UUID> boardIds, AllTicketQueryRequest request) {
        Specification<Ticket> spec = byBoardIds(boardIds);
        if (request == null) {
            return spec;
        }
        return spec
                .and(byStatuses(request.getStatuses()))
                .and(byTypes(request.getTypes()))
                .and(byPriorities(request.getPriorities()))
                .and(byAssignees(request.getAssigneeIds()))
                .and(byReviewers(request.getReviewerIds()))
                .and(byCreatedByIds(request.getCreatedByIds()))
                .and(byLabelIds(request.getLabelIds()))
                .and(byMilestoneId(request.getMilestoneId()))
                .and(byOnlyParentTickets(request.getMilestoneId()))
                .and(searchSpecification(request.getSearch(), boardIds, request.getMilestoneId(), null, null))
                .and(byDueDateRange(request.getDueDateStart(), request.getDueDateEnd()));
    }

    /**
     * Builds a {@link Specification} that resolves a single ticket by its exact key,
     * scoped to the supplied boards.
     * <p>
     * Unlike {@link #filterByBoards}, this intentionally omits the parent-only
     * restriction so sub-tickets are resolvable by key as well.
     *
     * @param boardIds the board IDs the ticket must belong to (user's accessible boards)
     * @param key      the exact ticket key to match
     * @return specification matching at most one ticket on an accessible board
     */
    public static Specification<Ticket> filterByBoardsAndKey(List<UUID> boardIds, String key) {
        return byBoardIds(boardIds).and(byExactKey(key));
    }

    /**
     * Builds a specification that matches the tickets with the given exact keys,
     * restricted to the supplied boards. Sub-tickets are included.
     *
     * A key is unique among live tickets, so each key matches at most one ticket; a key
     * of a ticket outside the boards does not match, so the caller can tell an
     * accessible ticket from one it may not read by the absence of the key in the result.
     *
     * @param boardIds the board IDs the tickets must belong to (the user's member boards)
     * @param keys     the exact ticket keys to match
     * @return specification matching the requested tickets on the given boards
     */
    public static Specification<Ticket> filterByBoardsAndKeys(List<UUID> boardIds, Collection<String> keys) {
        return byBoardIds(boardIds).and(byExactKeys(keys));
    }

    /**
     * Builds a specification that matches the given tickets, restricted to the supplied
     * boards. Sub-tickets are included.
     *
     * A ticket outside the boards does not match, so the caller can tell an accessible
     * ticket from one it may not read by the absence of the ID in the result.
     *
     * @param boardIds  the board IDs the tickets must belong to (the user's member boards)
     * @param ticketIds the ticket IDs to match
     * @return specification matching the requested tickets on the given boards
     */
    public static Specification<Ticket> filterByBoardsAndIds(List<UUID> boardIds, List<UUID> ticketIds) {
        return byBoardIds(boardIds).and(byIds(ticketIds));
    }

    /**
     * Builds a specification for the tickets one user is assigned to, in the given
     * statuses, restricted to the supplied boards. Sub-tickets are included, because a
     * sub-ticket is often the unit a person reports progress on.
     *
     * @param boardIds   the board IDs the tickets must belong to (the user's member boards)
     * @param assigneeId the user the tickets must be assigned to
     * @param statuses   the statuses to keep; null or empty keeps every status
     * @return specification matching the user's tickets on the given boards
     */
    public static Specification<Ticket> filterByBoardsAssigneeAndStatuses(List<UUID> boardIds, UUID assigneeId,
                                                                          List<TicketStatus> statuses) {
        return byBoardIds(boardIds)
                .and(byAssignees(List.of(assigneeId)))
                .and(byStatuses(statuses));
    }

    /**
     * Builds a composite {@link Specification} for filtering {@link Ticket}
     * entities.
     * <p>
     * The caller is responsible for resolving the correct board IDs and backlog
     * IDs (e.g. expanding to related boards when includeAllBoards is true).
     *
     * @param boardIds   the board IDs to scope tickets to
     * @param backlogIds the backlog IDs to filter by (nullable — no backlog filter when null/empty)
     * @param request    the query request containing filter criteria
     * @return combined specification with all applicable filters
     */
    public static Specification<Ticket> filterBy(List<UUID> boardIds, List<UUID> backlogIds,
                                                  TicketQueryRequest request) {
        Specification<Ticket> spec = byBoardIds(boardIds);
        if (request == null) {
            return spec;
        }
        return spec
                .and(byStatuses(request.getStatuses()))
                .and(byTypes(request.getTypes()))
                .and(byPriorities(request.getPriorities()))
                .and(bySprintId(request.getSprintId()))
                .and(byBacklogIds(backlogIds))
                .and(byProjectId(request.getProjectId()))
                .and(byAssignees(request.getAssigneeIds()))
                .and(byReviewers(request.getReviewerIds()))
                .and(byLabelIds(request.getLabelIds()))
                .and(byMilestoneId(request.getMilestoneId()))
                .and(byListRoots(request.getMilestoneId(), request.getSprintId(), backlogIds))
                .and(searchSpecification(request.getSearch(), boardIds, request.getMilestoneId(),
                        request.getSprintId(), backlogIds))
                .and(byDueDateRange(request.getDueDateStart(), request.getDueDateEnd()));
    }
}
