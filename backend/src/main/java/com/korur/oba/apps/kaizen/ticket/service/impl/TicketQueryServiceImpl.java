package com.korur.oba.apps.kaizen.ticket.service.impl;

// 1. Framework-level
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardSprint;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.helper.BoardHelper;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.service.SprintQueryService;
import com.korur.oba.apps.kaizen.ticket.constant.HomeDashboardConstant;
import com.korur.oba.apps.kaizen.ticket.constant.TicketConstant;
import com.korur.oba.apps.kaizen.ticket.constant.TicketExceptionLogConstant;
import com.korur.oba.apps.kaizen.ticket.dto.projection.LeaderboardTicketProjection;
import com.korur.oba.apps.kaizen.ticket.dto.projection.SprintListRootProjection;
import com.korur.oba.apps.kaizen.ticket.dto.projection.StaleBacklogTicketProjection;
import com.korur.oba.apps.kaizen.ticket.dto.request.AllTicketQueryRequest;
import com.korur.oba.apps.kaizen.ticket.dto.request.TicketQueryRequest;
import com.korur.oba.apps.kaizen.ticket.dto.response.AllTicketListResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeBurndownPointResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeLeaderboardEntryResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeLeaderboardResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeSprintResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeStaleBacklogResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeStaleTicketResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.HomeSummaryResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketDetailResponse;
import com.korur.oba.apps.kaizen.ticket.dto.response.TicketListResponse;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.entity.TicketAssignee;
import com.korur.oba.apps.kaizen.ticket.enums.HomeLeaderboardPeriod;
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;
import com.korur.oba.apps.kaizen.ticket.enums.TicketPriority;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.enums.TicketType;
import com.korur.oba.apps.kaizen.ticket.mapper.TicketMapper;
import com.korur.oba.apps.kaizen.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.apps.kaizen.ticket.service.TicketQueryService;
import com.korur.oba.apps.kaizen.ticket.specification.TicketSpecification;
import com.korur.oba.apps.kaizen.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.service.UserQueryService;

/**
 * Implementation of ticket read operations.
 *
 * All methods run within a read-only transaction for optimal database
 * performance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketQueryServiceImpl implements TicketQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TicketQueryServiceImpl.class);

    /**
     * Orders the focus board candidates by name, with the ID as the tie-breaker, so the
     * default focus board stays the same across requests.
     */
    private static final Comparator<Board> BOARD_BY_NAME = Comparator
            .comparing(Board::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Board::getId);

    /**
     * Orders ACTIVE sprints by start date, undated last, so a board that holds two shows
     * the one that started first.
     */
    private static final Comparator<Sprint> SPRINT_BY_START = Comparator
            .comparing(Sprint::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Sprint::getId);

    private final TicketRepository ticketRepository;
    private final TicketAssigneeRepository ticketAssigneeRepository;
    private final TicketValidator ticketValidator;
    private final TicketMapper ticketMapper;
    private final BoardHelper boardHelper;
    private final BoardAccessService boardAccessService;
    private final BoardMemberQueryService boardMemberQueryService;
    private final SprintQueryService sprintQueryService;
    private final UserQueryService userQueryService;
    private final LoggingService loggingService;
    private final Clock clock;
    private final BusinessCalendar businessCalendar;

    @Override
    public TicketDetailResponse getTicket(User user, UUID organizationId, UUID boardId, UUID ticketId) {
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);

        // Validate ticket exists and map to detail response.
        Ticket ticket = ticketValidator.ensureTicketExists(boardId, ticketId);
        return ticketMapper.toDetailResponse(ticket);
    }

    @Override
    public List<TicketListResponse> listTickets(User user, UUID organizationId, UUID boardId, TicketQueryRequest queryRequest) {
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);

        // Validate ticket placement. Either sprint or backlog should be provided.
        ticketValidator.ensureTicketHasCorrectPlacement(queryRequest.getSprintId(), queryRequest.getBacklogId());

        // Resolve board IDs and backlog IDs for the specification.
        // When includeAllBoards is true:
        // - Sprint scenario: don't restrict by board — sprints are many-to-many with
        // boards
        // and can span boards outside the parent-child hierarchy.
        // - Backlog scenario: expand to parent + child boards and their backlogs.
        boolean includeAll = Boolean.TRUE.equals(queryRequest.getIncludeAllBoards());
        boolean isSprintQuery = queryRequest.getSprintId() != null;

        List<Board> boards;
        List<UUID> backlogIds = null;

        if (includeAll) {
            List<Board> relatedBoards = boardHelper.resolveRelatedBoardEntities(board, user.getId());
            List<Board> allBoards = new ArrayList<>(relatedBoards);
            if (!allBoards.contains(board)) {
                allBoards.add(board);
            }
            boards = retainRequestedBoards(allBoards, queryRequest.getBoardIds());
            if (!isSprintQuery && queryRequest.getBacklogId() != null) {
                backlogIds = boards.stream().map(b -> b.getBacklog()).map(Backlog::getId).toList();
            }
        } else {
            boards = retainRequestedBoards(List.of(board), queryRequest.getBoardIds());
            if (queryRequest.getBacklogId() != null) {
                backlogIds = List.of(queryRequest.getBacklogId());
            }
        }

        // Early return if no boards are resolved — avoids a wasted database round-trip.
        List<UUID> boardIds = boards.stream().map(Board::getId).toList();
        if (boardIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Use specification-based filtering for dynamic queries.
        final Specification<Ticket> spec = TicketSpecification.filterBy(boardIds,
                backlogIds, queryRequest);
        List<Ticket> tickets = ticketRepository.findAll(spec, queryRequest.toSort());
        return tickets.stream().map(ticketMapper::toListResponse).toList();
    }

    @Override
    public List<AllTicketListResponse> listAllTickets(UUID userId, UUID organizationId, AllTicketQueryRequest queryRequest) {
        // Retrieve all board IDs where the user is a member.
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);

        // If the request specifies board IDs, intersect for security.
        List<UUID> effectiveBoardIds;
        if (queryRequest.getBoardIds() != null && !queryRequest.getBoardIds().isEmpty()) {
            effectiveBoardIds = new ArrayList<>(memberBoardIds);
            effectiveBoardIds.retainAll(queryRequest.getBoardIds());
        } else {
            effectiveBoardIds = memberBoardIds;
        }

        // Early return if no boards match — avoids a wasted database round-trip.
        if (effectiveBoardIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Build specification and query tickets.
        final Specification<Ticket> spec = TicketSpecification.filterByBoards(effectiveBoardIds, queryRequest);
        List<Ticket> tickets = ticketRepository.findAll(spec, queryRequest.toSort());
        return tickets.stream().map(ticketMapper::toAllTicketListResponse).toList();
    }

    @Override
    public AllTicketListResponse getTicketByKey(UUID userId, UUID organizationId, String key) {
        // Resolve all board IDs where the user is a member (identical scoping to listAllTickets).
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);

        // Build a board-scoped, exact-key specification. byOnlyParentTickets() is
        // intentionally omitted so sub-tickets resolve by key as well. An empty board
        // list yields a match-nothing predicate, so an inaccessible ticket is treated
        // as not found.
        final Specification<Ticket> spec = TicketSpecification.filterByBoardsAndKey(memberBoardIds, key);
        Ticket ticket = ticketRepository.findAll(spec).stream()
                .findFirst()
                .orElseThrow(() -> {
                    // Log for observability before propagating the not-found error.
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.SERVICE,
                            TicketExceptionLogConstant.TICKET_NOT_FOUND_BY_KEY,
                            key);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_TICKET_FOUND,
                            Map.of(TicketConstant.FIELD_TICKET_KEY, key));
                });

        return ticketMapper.toAllTicketListResponse(ticket);
    }

    @Override
    public Ticket getEntityById(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> {
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.SERVICE,
                            TicketExceptionLogConstant.TICKET_NOT_FOUND,
                            ticketId);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_TICKET_FOUND,
                            Map.of(TicketConstant.FIELD_TICKET_ID, ticketId));
                });
    }

    @Override
    public long countTicketsByBoardId(UUID boardId) {
        return ticketRepository.countByBoard_Id(boardId);
    }

    @Override
    public long countActiveTicketsByBoardIdAndAssigneeId(UUID boardId, UUID userId) {
        return ticketRepository.countByBoard_IdAndAssignees_User_IdAndStatusNot(
                boardId, userId, TicketStatus.DONE);
    }

    @Override
    public List<Ticket> getAccessibleTickets(UUID userId, UUID organizationId, List<UUID> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Membership of any role is enough to read a ticket, as on the ticket detail endpoint.
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);
        if (memberBoardIds.isEmpty()) {
            return Collections.emptyList();
        }

        final Specification<Ticket> spec = TicketSpecification.filterByBoardsAndIds(memberBoardIds, ticketIds);
        return ticketRepository.findAll(spec);
    }

    @Override
    public List<Ticket> getAccessibleTicketsByKeys(UUID userId, UUID organizationId, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        // Membership of any role is enough to read a ticket, as on the ticket detail endpoint.
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);
        if (memberBoardIds.isEmpty()) {
            return Collections.emptyList();
        }

        final Specification<Ticket> spec = TicketSpecification.filterByBoardsAndKeys(memberBoardIds, keys);
        return ticketRepository.findAll(spec);
    }

    @Override
    public List<AllTicketListResponse> listActiveAssignedTickets(UUID userId, UUID organizationId, int limit) {
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);
        if (memberBoardIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Sub-tickets stay in: progress is often reported on a sub-ticket rather than its parent.
        final Specification<Ticket> spec = TicketSpecification.filterByBoardsAssigneeAndStatuses(
                memberBoardIds, userId, List.of(TicketStatus.IN_PROGRESS));
        Pageable firstPage = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, TicketConstant.FIELD_TICKET_KEY));
        return ticketRepository.findAll(spec, firstPage)
                .stream()
                .map(ticketMapper::toAllTicketListResponse)
                .toList();
    }

    @Override
    public HomeSummaryResponse getHomeSummary(UUID userId, UUID organizationId) {
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);
        if (memberBoardIds.isEmpty()) {
            return ticketMapper.toHomeSummaryResponse(0L, 0L);
        }

        long openIncidents = ticketRepository.countRootsByBoardIdsAndTypeAndStatusNot(
                memberBoardIds, TicketType.INCIDENT, TicketStatus.DONE);

        // A sprint may be shared with boards the user is not a member of, so the count stays
        // restricted to the member boards and never reveals work on the other boards.
        List<UUID> activeSprintIds = sprintQueryService.getActiveSprintIdsByBoardIds(memberBoardIds);
        long openSprintTickets = activeSprintIds.isEmpty()
                ? 0L
                : ticketRepository.countSprintListRootsByBoardIdsAndSprintIdsAndStatusNot(
                        memberBoardIds, activeSprintIds, TicketStatus.DONE);

        return ticketMapper.toHomeSummaryResponse(openIncidents, openSprintTickets);
    }

    @Override
    public Map<UUID, List<Ticket>> getDoneActiveSprintTicketsByAssignees(UUID readerId, UUID organizationId,
                                                                         Collection<UUID> assigneeIds) {
        if (assigneeIds.isEmpty()) {
            return Map.of();
        }
        // Scoped to the reader's boards: a sprint may be shared with boards the reader is not
        // a member of, and an author's ticket there must never reach this reader.
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(readerId, organizationId);
        if (memberBoardIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> activeSprintIds = sprintQueryService.getActiveSprintIdsByBoardIds(memberBoardIds);
        if (activeSprintIds.isEmpty()) {
            return Map.of();
        }

        // The rows are assignments ordered by ticket key, so each assignee's list keeps that order.
        Map<UUID, List<Ticket>> ticketsByAssignee = new LinkedHashMap<>();
        for (TicketAssignee assignment : ticketAssigneeRepository.findByAssigneesBoardsSprintsAndStatus(
                assigneeIds, memberBoardIds, activeSprintIds, TicketStatus.DONE)) {
            ticketsByAssignee.computeIfAbsent(assignment.getUser().getId(), (UUID key) -> new ArrayList<>())
                    .add(assignment.getTicket());
        }
        return ticketsByAssignee;
    }

    @Override
    public HomeStaleBacklogResponse getStaleBacklog(UUID userId, UUID organizationId) {
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);
        if (memberBoardIds.isEmpty()) {
            return new HomeStaleBacklogResponse(0L, List.of());
        }

        Instant cutoff = clock.instant().minus(HomeDashboardConstant.STALE_BACKLOG_DAYS, ChronoUnit.DAYS);
        long total = ticketRepository.countStaleBacklogTickets(memberBoardIds, cutoff);
        if (total == 0L) {
            return new HomeStaleBacklogResponse(0L, List.of());
        }

        // A VIEWER sees the stale tickets of a board but may not delete them, so the page
        // hides the delete controls on those rows.
        Set<UUID> contributorBoardIds = new HashSet<>(boardMemberQueryService.getContributorBoardIds(userId, organizationId));
        List<HomeStaleTicketResponse> tickets = ticketRepository
                .findStaleBacklogTickets(memberBoardIds, cutoff, HomeDashboardConstant.STALE_BACKLOG_LIMIT)
                .stream()
                .map((StaleBacklogTicketProjection row) -> toStaleTicketResponse(
                        row, contributorBoardIds.contains(row.getBoardId())))
                .toList();
        return new HomeStaleBacklogResponse(total, tickets);
    }

    @Override
    public HomeLeaderboardResponse getLeaderboard(UUID userId, UUID organizationId, HomeLeaderboardPeriod period) {
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);
        if (period == HomeLeaderboardPeriod.SPRINT
                && !memberBoardIds.isEmpty()
                && !sprintQueryService.getActiveSprintIdsByBoardIds(memberBoardIds).isEmpty()) {
            return new HomeLeaderboardResponse(HomeLeaderboardPeriod.SPRINT, null, null,
                    rankActiveSprintAssignees(userId, organizationId, memberBoardIds));
        }

        // LAST_30_DAYS, also when SPRINT was asked for and no sprint is ACTIVE on the boards.
        Instant to = clock.instant();
        Instant from = to.minus(HomeDashboardConstant.LEADERBOARD_WINDOW_DAYS, ChronoUnit.DAYS);
        List<HomeLeaderboardEntryResponse> entries = memberBoardIds.isEmpty()
                ? List.of()
                : rankCompletedAssignments(ticketRepository.findCompletedTicketAssignments(memberBoardIds, from, to));
        return new HomeLeaderboardResponse(HomeLeaderboardPeriod.LAST_30_DAYS, from, to, entries);
    }

    @Override
    public HomeSprintResponse getHomeSprint(UUID userId, UUID organizationId, UUID boardId) {
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);
        Set<UUID> memberBoardIdSet = new HashSet<>(memberBoardIds);

        // Each of the user's boards with the ACTIVE sprint it runs. The sprints come in
        // start order, so a board linked to two keeps the one that started first.
        Map<UUID, Board> candidateBoards = new HashMap<>();
        Map<UUID, Sprint> sprintByBoardId = new HashMap<>();
        List<Sprint> activeSprints = sprintQueryService.getActiveSprintsByBoardIds(memberBoardIds).stream()
                .sorted(SPRINT_BY_START)
                .toList();
        for (Sprint sprint : activeSprints) {
            for (BoardSprint link : sprint.getBoardSprints()) {
                Board board = link.getBoard();
                if (memberBoardIdSet.contains(board.getId()) && !sprintByBoardId.containsKey(board.getId())) {
                    candidateBoards.put(board.getId(), board);
                    sprintByBoardId.put(board.getId(), sprint);
                }
            }
        }

        List<Board> candidates = candidateBoards.values().stream().sorted(BOARD_BY_NAME).toList();
        List<BoardListResponse> boards = candidates.stream().map(ticketMapper::toHomeBoardResponse).toList();
        Board focusBoard = resolveFocusBoard(candidates, boardId);
        if (focusBoard == null) {
            return new HomeSprintResponse(null, null, null, null, null, null, null, 0, 0, List.of(), boards);
        }

        Sprint sprint = sprintByBoardId.get(focusBoard.getId());
        List<SprintListRootProjection> scope = ticketRepository.findSprintListRoots(sprint.getId(), memberBoardIdSet);
        int openTickets = (int) scope.stream()
                .filter((SprintListRootProjection ticket) -> !TicketStatus.DONE.name().equals(ticket.getStatus()))
                .count();
        return new HomeSprintResponse(
                focusBoard.getId(),
                focusBoard.getName(),
                focusBoard.getSlug(),
                sprint.getId(),
                sprint.getName(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                scope.size(),
                openTickets,
                buildBurndown(sprint, scope),
                boards);
    }

    /**
     * Picks the focus board among the boards with an ACTIVE sprint. The requested board
     * is only a hint: one the user may not read, or one without an ACTIVE sprint, falls
     * back to the default instead of failing, so a stale link still opens the page.
     *
     * @param candidates       the user's boards with an ACTIVE sprint, ordered by name
     * @param requestedBoardId the requested board; may be null
     * @return the focus board, or null when there is no candidate
     */
    private Board resolveFocusBoard(List<Board> candidates, UUID requestedBoardId) {
        if (requestedBoardId != null) {
            for (Board candidate : candidates) {
                if (candidate.getId().equals(requestedBoardId)) {
                    return candidate;
                }
            }
        }
        // A top-level board runs the organisation-wide sprint its child boards share.
        for (Board candidate : candidates) {
            if (candidate.getParentBoard() == null) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Builds one burndown point per day of the sprint on the {@link BusinessCalendar},
     * the day of the daily updates, so the chart and the updates channel agree on where
     * a day ends. A ticket is open at the end of a day unless it reached DONE before that day
     * ended; days after today have no actual value yet.
     *
     * @param sprint the focus sprint
     * @param scope  the sprint's list roots on the user's boards
     * @return the points from the first to the last day, or empty without both dates
     */
    private List<HomeBurndownPointResponse> buildBurndown(Sprint sprint, List<SprintListRootProjection> scope) {
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            return List.of();
        }
        LocalDate firstDay = businessCalendar.dayOf(sprint.getStartDate());
        LocalDate lastDay = businessCalendar.dayOf(sprint.getEndDate());
        if (lastDay.isBefore(firstDay)) {
            return List.of();
        }
        LocalDate today = businessCalendar.today();
        long sprintDays = ChronoUnit.DAYS.between(firstDay, lastDay);
        BigDecimal total = BigDecimal.valueOf(scope.size());

        List<HomeBurndownPointResponse> points = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            Integer remaining = day.isAfter(today)
                    ? null
                    : countOpenAt(scope, businessCalendar.startOfDay(day.plusDays(1)));
            // A one-day sprint has no slope; its single day is the deadline, so ideal is 0.
            BigDecimal ideal = sprintDays == 0
                    ? BigDecimal.ZERO.setScale(HomeDashboardConstant.BURNDOWN_IDEAL_SCALE)
                    : total.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(day, lastDay)))
                            .divide(BigDecimal.valueOf(sprintDays),
                                    HomeDashboardConstant.BURNDOWN_IDEAL_SCALE, RoundingMode.HALF_UP);
            points.add(new HomeBurndownPointResponse(day, remaining, ideal));
        }
        return points;
    }

    /**
     * Counts the tickets that had not reached DONE before the given moment.
     *
     * @param scope  the tickets
     * @param moment the end of the day being counted, exclusive
     * @return the open count at that moment
     */
    private static int countOpenAt(List<SprintListRootProjection> scope, Instant moment) {
        int open = 0;
        for (SprintListRootProjection ticket : scope) {
            if (ticket.getCompletedAt() == null || !ticket.getCompletedAt().isBefore(moment)) {
                open++;
            }
        }
        return open;
    }

    /**
     * Ranks the members of the reader's boards by the tickets they finished in the ACTIVE
     * sprints, reusing the definition of the daily update cards. A ticket's assignee count
     * is the number of assignee lists it appears in, so its points split only among the
     * assignees who are members of the reader's boards.
     *
     * @param readerId       the user whose board memberships scope the tickets
     * @param organizationId the organization of the request
     * @param memberBoardIds the reader's boards; must not be empty
     * @return the ranked entries
     */
    private List<HomeLeaderboardEntryResponse> rankActiveSprintAssignees(UUID readerId, UUID organizationId,
                                                                         List<UUID> memberBoardIds) {
        Map<UUID, List<Ticket>> ticketsByAssignee = getDoneActiveSprintTicketsByAssignees(
                readerId, organizationId, boardMemberQueryService.getMemberUserIds(memberBoardIds));
        if (ticketsByAssignee.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> assigneeCountByTicket = new HashMap<>();
        for (List<Ticket> tickets : ticketsByAssignee.values()) {
            for (Ticket ticket : tickets) {
                assigneeCountByTicket.merge(ticket.getId(), 1, Integer::sum);
            }
        }

        Map<UUID, LeaderboardTally> tallies = new HashMap<>();
        for (User assignee : userQueryService.getUserEntitiesByIds(new ArrayList<>(ticketsByAssignee.keySet()))) {
            LeaderboardTally tally = new LeaderboardTally(
                    assignee.getId(), assignee.getProfile().getFirstName(), assignee.getProfile().getLastName(),
                    assignee.getEmail());
            for (Ticket ticket : ticketsByAssignee.get(assignee.getId())) {
                tally.credit(ticket.getMissionRank(), assigneeCountByTicket.get(ticket.getId()));
            }
            tallies.put(assignee.getId(), tally);
        }
        return toRankedEntries(tallies.values());
    }

    /**
     * Ranks the assignees of the finished tickets read for a time window.
     *
     * @param assignments one row per assignment of a finished ticket
     * @return the ranked entries
     */
    private List<HomeLeaderboardEntryResponse> rankCompletedAssignments(List<LeaderboardTicketProjection> assignments) {
        Map<UUID, LeaderboardTally> tallies = new HashMap<>();
        for (LeaderboardTicketProjection assignment : assignments) {
            tallies.computeIfAbsent(assignment.getUserId(), (UUID key) -> new LeaderboardTally(
                            assignment.getUserId(), assignment.getFirstName(), assignment.getLastName(),
                            assignment.getEmail()))
                    .credit(MissionRank.valueOf(assignment.getMissionRank()),
                            assignment.getAssigneeCount().intValue());
        }
        return toRankedEntries(tallies.values());
    }

    /**
     * Rounds each tally to the response scale and orders them: most points first, then
     * most tickets, then last name, with the user ID as the last tie-breaker so equal
     * entries keep one order across requests.
     *
     * @param tallies the per-user tallies
     * @return the ranked entries
     */
    private List<HomeLeaderboardEntryResponse> toRankedEntries(Collection<LeaderboardTally> tallies) {
        return tallies.stream()
                .map((LeaderboardTally tally) -> new HomeLeaderboardEntryResponse(
                        tally.userId, tally.firstName, tally.lastName, tally.email,
                        tally.points.setScale(HomeDashboardConstant.LEADERBOARD_POINTS_SCALE, RoundingMode.HALF_UP),
                        tally.tickets))
                .sorted(Comparator.comparing(HomeLeaderboardEntryResponse::points).reversed()
                        .thenComparing(Comparator.comparingInt(HomeLeaderboardEntryResponse::tickets).reversed())
                        .thenComparing(HomeLeaderboardEntryResponse::lastName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(HomeLeaderboardEntryResponse::userId))
                .toList();
    }

    /**
     * Maps a stale backlog row to its response; the enum columns arrive as text.
     *
     * @param row       the stale backlog row
     * @param canDelete whether the reader may delete the ticket
     * @return the response
     */
    private HomeStaleTicketResponse toStaleTicketResponse(StaleBacklogTicketProjection row, boolean canDelete) {
        return new HomeStaleTicketResponse(
                row.getId(),
                row.getKey(),
                row.getTitle(),
                TicketStatus.valueOf(row.getStatus()),
                TicketPriority.valueOf(row.getPriority()),
                MissionRank.valueOf(row.getMissionRank()),
                row.getBoardId(),
                row.getBoardName(),
                row.getBoardSlug(),
                row.getLastActivityAt(),
                canDelete);
    }

    /**
     * Narrows the boards a board ticket list covers to the requested subset.
     *
     * The requested IDs only select among boards the caller may already list, so an ID
     * of an unrelated board is dropped instead of widening access.
     *
     * @param boards            the boards resolved for the list
     * @param requestedBoardIds the requested subset; null or empty keeps every board
     * @return the boards whose ID is in the requested subset
     */
    private List<Board> retainRequestedBoards(List<Board> boards, List<UUID> requestedBoardIds) {
        if (requestedBoardIds == null || requestedBoardIds.isEmpty()) {
            return boards;
        }
        return boards.stream()
                .filter((Board candidate) -> requestedBoardIds.contains(candidate.getId()))
                .toList();
    }

    /**
     * The running leaderboard score of one user. Each split share is kept at a working
     * scale and only the total is rounded, so three thirds add up to one whole ticket.
     */
    private static final class LeaderboardTally {

        private final UUID userId;
        private final String firstName;
        private final String lastName;
        private final String email;
        private BigDecimal points = BigDecimal.ZERO;
        private int tickets;

        private LeaderboardTally(UUID userId, String firstName, String lastName, String email) {
            this.userId = userId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
        }

        /**
         * Credits the user with one finished ticket and that ticket's share of points.
         *
         * @param missionRank   the ticket's size
         * @param assigneeCount the number of assignees the points are split between
         */
        private void credit(MissionRank missionRank, int assigneeCount) {
            BigDecimal share = BigDecimal.valueOf(HomeDashboardConstant.MISSION_RANK_POINTS.get(missionRank))
                    .divide(BigDecimal.valueOf(assigneeCount),
                            HomeDashboardConstant.LEADERBOARD_POINTS_WORKING_SCALE, RoundingMode.HALF_UP);
            points = points.add(share);
            tickets++;
        }
    }
}
