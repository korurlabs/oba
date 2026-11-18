package com.korur.oba.apps.kaizen.sprint.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardSprint;
import com.korur.oba.apps.kaizen.board.enums.BoardMemberRole;
import com.korur.oba.apps.kaizen.board.repository.BoardSprintRepository;
import com.korur.oba.apps.kaizen.board.service.BoardAccessService;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.sprint.dto.response.SprintChildBoardResponse;
import com.korur.oba.apps.kaizen.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kaizen.sprint.dto.response.SprintListResponse;
import com.korur.oba.apps.kaizen.sprint.dto.response.SprintStartConflictResponse;
import com.korur.oba.apps.kaizen.sprint.dto.response.SprintStartPreviewResponse;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;
import com.korur.oba.apps.kaizen.sprint.enums.SprintStatus;
import com.korur.oba.apps.kaizen.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kaizen.sprint.helper.SprintRecurrenceCalculator;
import com.korur.oba.apps.kaizen.sprint.mapper.SprintMapper;
import com.korur.oba.apps.kaizen.sprint.repository.SprintRepository;
import com.korur.oba.apps.kaizen.sprint.service.SprintQueryService;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of sprint read operations.
 * <p>
 * All methods run within a read-only transaction for optimal database performance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SprintQueryServiceImpl implements SprintQueryService {

    private final SprintRepository sprintRepository;
    private final BoardSprintRepository boardSprintRepository;
    private final SprintMapper sprintMapper;
    private final BoardAccessService boardAccessService;
    private final BoardMemberQueryService boardMemberQueryService;
    private final SprintLifecycleHelper sprintLifecycleHelper;
    private final TicketRepository ticketRepository;
    private final SprintRecurrenceCalculator sprintRecurrenceCalculator;

    @Override
    public SprintDetailResponse getSprint(User user, UUID organizationId, UUID boardId, UUID sprintId) {
        // Validate board and sprint exist, and user has viewer permissions.
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.VIEWER);
        return sprintMapper.toDetailResponse(sprint);
    }

    @Override
    public List<SprintListResponse> listSprints(User user, UUID organizationId, UUID boardId) {
        // Validate board exists and user has viewer permissions.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
        // Read the board's links to live sprints, since each link holds the sprint's slug on this board.
        List<BoardSprint> boardSprints = boardSprintRepository.findByBoardIdWithLiveSprint(boardId);
        return boardSprints.stream()
                .map(boardSprint -> sprintMapper.toListResponse(boardSprint.getSprint(), boardSprint.getSlug(),
                        boardSprint.getSprint().getStatus() == SprintStatus.ACTIVE
                                ? seriesEndedByClosing(boardSprint.getSprint())
                                : null))
                .toList();
    }

    /**
     * The name of the enabled series that closing the given sprint would leave without a
     * running or planned sprint. Closing it switches that series off, so a takeover names it
     * before the user confirms instead of stopping the repeat without a word.
     *
     * @param sprint the ACTIVE sprint a takeover would close
     * @return the series name, or null when the sprint is in no enabled series or the series keeps running
     */
    private String seriesEndedByClosing(Sprint sprint) {
        SprintSeries series = sprint.getSeries();
        if (series == null || !series.isEnabled()) {
            return null;
        }
        List<Sprint> seriesSprints = sprintRepository.findBySeries_IdOrderByRoundDesc(series.getId());
        return sprintRecurrenceCalculator.closingEndsSeries(sprint, seriesSprints) ? series.getName() : null;
    }

    @Override
    public List<SprintChildBoardResponse> listChildBoards(User user, UUID organizationId, UUID boardId) {
        // Only sprint creators use this list, so the same role as sprint create is required.
        Board rootBoard = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        Set<UUID> memberBoardIds = new HashSet<>(boardMemberQueryService.getMemberBoardIds(
                user.getId(), organizationId));
        Set<UUID> adminBoardIds = new HashSet<>(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId));
        List<SprintChildBoardResponse> result = new ArrayList<>();
        Set<UUID> visitedBoardIds = new HashSet<>();
        visitedBoardIds.add(rootBoard.getId());
        collectChildBoards(rootBoard, 1, memberBoardIds, adminBoardIds, visitedBoardIds, result);
        return result;
    }

    @Override
    public SprintStartPreviewResponse getStartPreview(User user, UUID organizationId, UUID boardId, UUID sprintId) {
        // Only sprint managers start sprints, so the preview needs the same role as the start.
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN);
        Set<UUID> memberBoardIds = new HashSet<>(boardMemberQueryService.getMemberBoardIds(
                user.getId(), organizationId));
        Set<UUID> adminBoardIds = new HashSet<>(boardMemberQueryService.getAdminBoardIds(user.getId(), organizationId));

        // Position of each board in this sprint's links; boards it is not linked to sort last.
        Map<UUID, Integer> linkPositions = new HashMap<>();
        List<BoardSprint> links = sprint.getBoardSprints();
        for (int position = 0; position < links.size(); position++) {
            linkPositions.putIfAbsent(links.get(position).getBoard().getId(), position);
        }

        // The lookup returns the blocking sprints in no particular order, so they are put in the
        // order of the first board they share with this sprint before the rows are built.
        List<Sprint> blockingSprints = sprintLifecycleHelper.findOtherActiveSprintsOnLinkedBoards(sprint).stream()
                .sorted(Comparator.comparingInt((Sprint blocking) -> firstSharedLinkPosition(blocking, linkPositions))
                        .thenComparing(Sprint::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        boolean canTakeOver = true;
        List<SprintStartConflictResponse> conflicts = new ArrayList<>();
        for (Sprint blockingSprint : blockingSprints) {
            String endsSeriesName = seriesEndedByClosing(blockingSprint);
            Map<UUID, Long> unfinishedCountByBoardId = ticketRepository
                    .findBySprint_IdAndStatusNot(blockingSprint.getId(), TicketStatus.DONE).stream()
                    .collect(Collectors.groupingBy((Ticket ticket) -> ticket.getBoard().getId(), Collectors.counting()));
            for (BoardSprint blockingLink : blockingSprint.getBoardSprints()) {
                Board board = blockingLink.getBoard();
                boolean canManage = adminBoardIds.contains(board.getId());
                // A takeover is refused for any board the caller does not manage, listed or not.
                canTakeOver = canTakeOver && canManage;
                if (!memberBoardIds.contains(board.getId())) {
                    // Boards the caller cannot see are never named.
                    continue;
                }
                long unfinishedTicketCount = unfinishedCountByBoardId.getOrDefault(board.getId(), 0L);
                boolean movesToBacklog = !linkPositions.containsKey(board.getId());
                conflicts.add(sprintMapper.toStartConflictResponse(
                        board, blockingSprint, unfinishedTicketCount, movesToBacklog, canManage, endsSeriesName));
            }
        }

        // Stable sort: rows of boards linked to this sprint follow its link order; the others
        // keep the blocking sprint order they were built in.
        List<SprintStartConflictResponse> orderedConflicts = conflicts.stream()
                .sorted(Comparator.comparingInt((SprintStartConflictResponse conflict) ->
                        linkPositions.getOrDefault(conflict.boardId(), Integer.MAX_VALUE)))
                .toList();
        return new SprintStartPreviewResponse(canTakeOver, orderedConflicts);
    }

    @Override
    public List<UUID> getActiveSprintIdsByBoardIds(Collection<UUID> boardIds) {
        // An empty IN list is not valid JPQL, and no boards means no sprints.
        if (boardIds == null || boardIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sprintRepository.findIdsByBoardIdsAndStatus(boardIds, SprintStatus.ACTIVE);
    }

    @Override
    public List<Sprint> getActiveSprintsByBoardIds(Collection<UUID> boardIds) {
        List<UUID> activeSprintIds = getActiveSprintIdsByBoardIds(boardIds);
        if (activeSprintIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sprintRepository.findAllById(activeSprintIds);
    }

    /**
     * Returns the earliest position, in the starting sprint's links, of a board the blocking
     * sprint is also linked to.
     */
    private int firstSharedLinkPosition(Sprint blockingSprint, Map<UUID, Integer> linkPositions) {
        return blockingSprint.getBoardSprints().stream()
                .map((BoardSprint link) -> linkPositions.get(link.getBoard().getId()))
                .filter(Objects::nonNull)
                .min(Integer::compare)
                .orElse(Integer.MAX_VALUE);
    }

    /**
     * Walks the children of a board depth-first, by name, and adds each board the user is a
     * member of. Boards the user is not a member of are not revealed, but the walk goes on below
     * them, so a member grandchild under a non-member child is still offered. The visited set
     * stops the walk if the stored hierarchy ever contains a cycle.
     *
     * An entry carries its parent's id only when the user can see that parent: the root board
     * at depth 1, which the user passed the ADMIN check on, or a board the user is a member of.
     * Below a non-member parent the id is null, so the listing never exposes the id of a board
     * the user cannot see.
     */
    private void collectChildBoards(Board parent, int depth, Set<UUID> memberBoardIds, Set<UUID> adminBoardIds,
                                    Set<UUID> visitedBoardIds, List<SprintChildBoardResponse> result) {
        List<Board> children = parent.getChildBoards().stream()
                .sorted(Comparator.comparing(Board::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        boolean parentVisible = depth == 1 || memberBoardIds.contains(parent.getId());
        UUID visibleParentId = parentVisible ? parent.getId() : null;
        for (Board child : children) {
            if (!visitedBoardIds.add(child.getId())) {
                continue;
            }
            if (memberBoardIds.contains(child.getId())) {
                // A board can hold two ACTIVE sprints after a manual start of a shared sprint;
                // the listing reports the latest instead of failing.
                Sprint activeSprint = sprintRepository
                        .findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(child.getId(), SprintStatus.ACTIVE)
                        .orElse(null);
                boolean canLink = adminBoardIds.contains(child.getId());
                String activeSprintEndsSeriesName = activeSprint == null ? null : seriesEndedByClosing(activeSprint);
                result.add(sprintMapper.toChildBoardResponse(
                        child, visibleParentId, depth, canLink, activeSprint, activeSprintEndsSeriesName));
            }
            collectChildBoards(child, depth + 1, memberBoardIds, adminBoardIds, visitedBoardIds, result);
        }
    }
}
