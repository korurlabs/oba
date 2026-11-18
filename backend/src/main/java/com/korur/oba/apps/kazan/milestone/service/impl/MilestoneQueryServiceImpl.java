package com.korur.oba.apps.kazan.milestone.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.helper.BoardHelper;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.milestone.dto.response.HomeDeadlineResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneListResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.RoadmapBoardResponse;
import com.korur.oba.apps.kazan.milestone.dto.response.RoadmapResponse;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneStatus;
import com.korur.oba.apps.kazan.milestone.helper.MilestoneHelper;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kazan.milestone.service.MilestoneQueryService;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.ticket.constant.HomeDashboardConstant;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of milestone and roadmap read operations.
 *
 * All methods run within a read-only transaction. Every response carries
 * progress from a single aggregate count query.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilestoneQueryServiceImpl implements MilestoneQueryService {

    private final MilestoneRepository milestoneRepository;
    private final MilestoneValidator milestoneValidator;
    private final MilestoneHelper milestoneHelper;
    private final BoardAccessService boardAccessService;
    private final BoardMemberQueryService boardMemberQueryService;
    private final BoardHelper boardHelper;
    private final Clock clock;

    @Override
    public List<MilestoneListResponse> listMilestones(User user, UUID organizationId, UUID boardId,
            MilestoneStatus status, boolean includeRelatedBoards) {
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);

        // Related boards are limited to those the user is a member of.
        List<UUID> boardIds = new ArrayList<>();
        boardIds.add(board.getId());
        if (includeRelatedBoards) {
            boardHelper.resolveRelatedBoardEntities(board, user.getId())
                    .forEach(related -> boardIds.add(related.getId()));
        }

        List<Milestone> milestones = status == null
                ? milestoneRepository.findByBoard_IdIn(boardIds)
                : milestoneRepository.findByBoard_IdInAndStatus(boardIds, status);
        return milestoneHelper.toListResponses(milestones, boardMemberQueryService.getMemberBoardIds(
                user.getId(), organizationId));
    }

    @Override
    public List<HomeDeadlineResponse> listHomeDeadlines(UUID userId, UUID organizationId) {
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(userId, organizationId);
        if (memberBoardIds.isEmpty()) {
            return List.of();
        }

        // The window is judged on the UTC date, the calendar milestone dates and their
        // health use. Overdue milestones stay listed until they are closed.
        LocalDate windowEnd = LocalDate.now(clock.withZone(ZoneOffset.UTC))
                .plusDays(HomeDashboardConstant.DEADLINE_WINDOW_DAYS);
        List<Milestone> due = milestoneRepository.findByBoard_IdInAndStatus(memberBoardIds, MilestoneStatus.OPEN)
                .stream()
                .filter((Milestone milestone) -> !milestone.getTargetDate().isAfter(windowEnd))
                .sorted(Comparator.comparing(Milestone::getTargetDate)
                        .thenComparing(Milestone::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(HomeDashboardConstant.DEADLINE_LIMIT)
                .toList();
        if (due.isEmpty()) {
            return List.of();
        }

        // The list response carries progress and health but not the board slug the
        // roadmap link needs, so the slug comes from the loaded boards.
        Map<UUID, Board> boardsByMilestoneId = new HashMap<>();
        for (Milestone milestone : due) {
            boardsByMilestoneId.put(milestone.getId(), milestone.getBoard());
        }
        return milestoneHelper.toListResponses(due, memberBoardIds).stream()
                .map((MilestoneListResponse milestone) -> new HomeDeadlineResponse(
                        milestone.id(),
                        milestone.name(),
                        milestone.targetDate(),
                        milestone.health(),
                        milestone.doneCount(),
                        milestone.totalCount(),
                        milestone.color(),
                        milestone.boardId(),
                        milestone.boardName(),
                        boardsByMilestoneId.get(milestone.id()).getSlug()))
                .toList();
    }

    @Override
    public MilestoneDetailResponse getMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.VIEWER);
        return milestoneHelper.toDetailResponse(milestone, boardMemberQueryService.getMemberBoardIds(
                user.getId(), organizationId));
    }

    @Override
    public RoadmapResponse getRoadmap(User user, UUID organizationId, UUID boardId, boolean includeChildBoards,
            LocalDate from, LocalDate to) {
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);
        milestoneValidator.ensureWindowValid(from, to);

        // Lanes: the board first, then its direct children the user is a member of, by name.
        Set<UUID> memberBoardIds = new HashSet<>(boardMemberQueryService.getMemberBoardIds(
                user.getId(), organizationId));
        List<Board> lanes = new ArrayList<>();
        lanes.add(board);
        if (includeChildBoards) {
            board.getChildBoards().stream()
                    .filter(child -> memberBoardIds.contains(child.getId()))
                    .sorted(Comparator.comparing(Board::getName, String.CASE_INSENSITIVE_ORDER))
                    .forEach(lanes::add);
        }

        // One milestone query for all lanes, filtered to the milestones whose span overlaps the window.
        List<UUID> laneIds = lanes.stream().map(Board::getId).toList();
        List<Milestone> milestones = milestoneRepository.findByBoard_IdIn(laneIds).stream()
                .filter(milestone -> overlapsWindow(milestone, from, to))
                .toList();

        // One count query for all milestones; responses come back sorted by target date.
        Map<UUID, List<MilestoneListResponse>> milestonesByBoard = new LinkedHashMap<>();
        for (UUID laneId : laneIds) {
            milestonesByBoard.put(laneId, new ArrayList<>());
        }
        for (MilestoneListResponse response : milestoneHelper.toListResponses(milestones, memberBoardIds)) {
            milestonesByBoard.get(response.boardId()).add(response);
        }

        List<RoadmapBoardResponse> boardResponses = lanes.stream()
                .map(lane -> new RoadmapBoardResponse(
                        lane.getId(),
                        lane.getName(),
                        lane.getPrefix(),
                        lane.getColor(),
                        milestonesByBoard.get(lane.getId())))
                .toList();
        return new RoadmapResponse(from, to, boardResponses);
    }

    /**
     * A milestone spans from its start date (or its target date without one) to
     * its target date; it is kept when that span overlaps the window.
     */
    private static boolean overlapsWindow(Milestone milestone, LocalDate from, LocalDate to) {
        if (from != null && milestone.getTargetDate().isBefore(from)) {
            return false;
        }
        LocalDate spanStart = milestone.getStartDate() != null
                ? milestone.getStartDate()
                : milestone.getTargetDate();
        return to == null || !spanStart.isAfter(to);
    }
}
