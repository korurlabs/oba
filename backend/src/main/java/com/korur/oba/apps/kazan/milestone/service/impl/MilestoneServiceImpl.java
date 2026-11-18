package com.korur.oba.apps.kazan.milestone.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.milestone.constant.service.MilestoneServiceLogConstant;
import com.korur.oba.apps.kazan.milestone.dto.request.MilestoneCreateRequest;
import com.korur.oba.apps.kazan.milestone.dto.request.MilestoneTicketsRequest;
import com.korur.oba.apps.kazan.milestone.dto.request.MilestoneUpdateRequest;
import com.korur.oba.apps.kazan.milestone.dto.response.MilestoneDetailResponse;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.entity.MilestoneDependency;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneStatus;
import com.korur.oba.apps.kazan.milestone.helper.MilestoneHelper;
import com.korur.oba.apps.kazan.milestone.mapper.MilestoneMapper;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kazan.milestone.repository.MilestoneRepository;
import com.korur.oba.apps.kazan.milestone.service.MilestoneService;
import com.korur.oba.apps.kazan.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of milestone write operations.
 *
 * Every method checks board access first, then validates, as sprint writes do.
 */
@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MilestoneServiceImpl.class);

    private final MilestoneRepository milestoneRepository;
    private final MilestoneDependencyRepository milestoneDependencyRepository;
    private final TicketRepository ticketRepository;
    private final BoardAccessService boardAccessService;
    private final BoardMemberQueryService boardMemberQueryService;
    private final MilestoneValidator milestoneValidator;
    private final TicketValidator ticketValidator;
    private final MilestoneMapper milestoneMapper;
    private final MilestoneHelper milestoneHelper;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public MilestoneDetailResponse createMilestone(
            User user, UUID organizationId, UUID boardId, MilestoneCreateRequest request) {
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        milestoneValidator.ensureStartNotAfterTarget(request.startDate(), request.targetDate());

        Milestone milestone = milestoneMapper.toEntity(request);
        milestone.setBoard(board);
        milestone.setStatus(MilestoneStatus.OPEN);
        // Without a chosen colour the bar looks like its board, as before colours existed.
        if (milestone.getColor() == null) {
            milestone.setColor(board.getColor());
        }
        milestone = milestoneRepository.save(milestone);

        // Any rejected dependency throws and rolls the whole create back.
        List<UUID> visibleBoardIds = visibleBoardIds(user, organizationId);
        if (request.dependsOnIds() != null) {
            for (UUID dependsOnId : request.dependsOnIds().stream().distinct().toList()) {
                addDependencyEdge(milestone, dependsOnId, visibleBoardIds);
            }
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_CREATED, milestone.getId());

        return milestoneHelper.toDetailResponse(milestone, visibleBoardIds);
    }

    @Override
    @Transactional
    public MilestoneDetailResponse updateMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId,
            MilestoneUpdateRequest request) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.ADMIN);

        // Null fields are left unchanged; the clear flags are the only way to null a field.
        milestoneMapper.updateEntity(milestone, request);
        if (Boolean.TRUE.equals(request.clearStartDate())) {
            milestone.setStartDate(null);
        }
        if (Boolean.TRUE.equals(request.clearDescription())) {
            milestone.setDescription(null);
        }

        // Validate the merged dates: either side may have changed.
        milestoneValidator.ensureStartNotAfterTarget(milestone.getStartDate(), milestone.getTargetDate());

        milestone = milestoneRepository.save(milestone);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_UPDATED, milestoneId);

        return milestoneHelper.toDetailResponse(milestone, visibleBoardIds(user, organizationId));
    }

    @Override
    @Transactional
    public MilestoneDetailResponse closeMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.ADMIN);
        milestoneValidator.ensureMilestoneIsOpen(milestone);

        milestone.setStatus(MilestoneStatus.CLOSED);
        milestone = milestoneRepository.save(milestone);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_CLOSED, milestoneId);

        return milestoneHelper.toDetailResponse(milestone, visibleBoardIds(user, organizationId));
    }

    @Override
    @Transactional
    public MilestoneDetailResponse reopenMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.ADMIN);
        milestoneValidator.ensureMilestoneIsClosed(milestone);

        milestone.setStatus(MilestoneStatus.OPEN);
        milestone = milestoneRepository.save(milestone);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_REOPENED, milestoneId);

        return milestoneHelper.toDetailResponse(milestone, visibleBoardIds(user, organizationId));
    }

    @Override
    @Transactional
    public MilestoneDetailResponse linkTickets(User user, UUID organizationId, UUID boardId, UUID milestoneId,
            MilestoneTicketsRequest request) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.ADMIN);
        List<Ticket> tickets = loadTicketsWithMemberAccess(user, organizationId, request);

        // Validate every ticket before changing any, so the request is all or nothing.
        for (Ticket ticket : tickets) {
            milestoneValidator.ensureMilestoneLinkableToBoard(milestone, ticket.getBoard());
        }
        for (Ticket ticket : tickets) {
            ticket.setMilestone(milestone);
        }
        ticketRepository.saveAll(tickets);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_TICKETS_LINKED, tickets.size(), milestoneId);

        return milestoneHelper.toDetailResponse(milestone, visibleBoardIds(user, organizationId));
    }

    @Override
    @Transactional
    public MilestoneDetailResponse unlinkTickets(User user, UUID organizationId, UUID boardId, UUID milestoneId,
            MilestoneTicketsRequest request) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.ADMIN);
        List<Ticket> tickets = loadTicketsWithMemberAccess(user, organizationId, request);

        // Only detach tickets that point at this milestone; another milestone's link stays.
        List<Ticket> attached = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (ticket.getMilestone() != null && ticket.getMilestone().getId().equals(milestoneId)) {
                ticket.setMilestone(null);
                attached.add(ticket);
            }
        }
        ticketRepository.saveAll(attached);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_TICKETS_UNLINKED, attached.size(), milestoneId);

        return milestoneHelper.toDetailResponse(milestone, visibleBoardIds(user, organizationId));
    }

    @Override
    @Transactional
    public MilestoneDetailResponse addDependency(
            User user, UUID organizationId, UUID boardId, UUID milestoneId, UUID dependsOnId) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.ADMIN);
        List<UUID> visibleBoardIds = visibleBoardIds(user, organizationId);
        addDependencyEdge(milestone, dependsOnId, visibleBoardIds);

        return milestoneHelper.toDetailResponse(milestone, visibleBoardIds);
    }

    @Override
    @Transactional
    public MilestoneDetailResponse removeDependency(
            User user, UUID organizationId, UUID boardId, UUID milestoneId, UUID dependsOnId) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.ADMIN);
        List<UUID> visibleBoardIds = visibleBoardIds(user, organizationId);
        milestoneValidator.ensureMilestoneExistsOnBoards(dependsOnId, visibleBoardIds);
        MilestoneDependency dependency = milestoneValidator.ensureDependencyExists(milestoneId, dependsOnId);

        milestoneDependencyRepository.delete(dependency);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_DEPENDENCY_REMOVED, milestoneId, dependsOnId);

        return milestoneHelper.toDetailResponse(milestone, visibleBoardIds);
    }

    @Override
    @Transactional
    public void deleteMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId) {
        Milestone milestone = boardAccessService.verifyMilestoneAccess(
                organizationId, boardId, milestoneId, user.getId(), BoardMemberRole.ADMIN);

        // Detach first: a soft-deleted milestone keeps its row, so the foreign key
        // alone would leave tickets pointing at it.
        int detached = ticketRepository.clearMilestoneByMilestoneIds(List.of(milestoneId));
        milestoneRepository.delete(milestone);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_DELETED, milestoneId, detached);
    }

    @Override
    @Transactional
    public void restoreMilestone(User user, UUID organizationId, UUID boardId, UUID milestoneId) {
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        Milestone milestone = milestoneValidator.ensureMilestoneExistsIncludingDeleted(boardId, milestoneId);
        milestoneValidator.ensureMilestoneIsDeleted(milestone);

        milestoneRepository.restoreById(milestoneId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_RESTORED, milestoneId);
    }

    @Override
    @Transactional
    public void deleteBoardMilestones(UUID boardId) {
        List<UUID> milestoneIds = milestoneRepository.findIdsByBoardId(boardId);
        if (milestoneIds.isEmpty()) {
            return;
        }
        int detached = ticketRepository.clearMilestoneByMilestoneIds(milestoneIds);
        int deleted = milestoneRepository.softDeleteAllByBoardId(boardId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.BOARD_MILESTONES_DELETED, deleted, boardId, detached);
    }

    @Override
    @Transactional
    public void restoreBoardMilestones(UUID boardId) {
        int restored = milestoneRepository.restoreAllByBoardId(boardId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.BOARD_MILESTONES_RESTORED, restored, boardId);
    }

    /**
     * Records that milestone depends on dependsOnId, under the one set of rules
     * shared by create and the dependency endpoint: the other milestone must be
     * live on a board the user is a member of, and the pair must pass the self,
     * related-board and cycle checks. An existing pair is left unchanged.
     */
    private void addDependencyEdge(Milestone milestone, UUID dependsOnId, List<UUID> visibleBoardIds) {
        UUID milestoneId = milestone.getId();
        Milestone dependsOn = milestoneValidator.ensureMilestoneExistsOnBoards(dependsOnId, visibleBoardIds);
        milestoneValidator.ensureDependencyAllowed(milestone, dependsOn);

        if (milestoneDependencyRepository.existsByMilestoneIdAndDependsOnMilestoneId(milestoneId, dependsOnId)) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    MilestoneServiceLogConstant.MILESTONE_DEPENDENCY_EXISTS, milestoneId, dependsOnId);
            return;
        }
        milestoneDependencyRepository.save(MilestoneDependency.builder()
                .milestoneId(milestoneId)
                .dependsOnMilestoneId(dependsOnId)
                .build());
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                MilestoneServiceLogConstant.MILESTONE_DEPENDENCY_ADDED, milestoneId, dependsOnId);
    }

    /**
     * Boards the user is a member of: responses only name dependencies on their milestones.
     */
    private List<UUID> visibleBoardIds(User user, UUID organizationId) {
        return boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId);
    }

    /**
     * Loads the requested tickets and checks MEMBER access on each distinct ticket board:
     * a board admin may only move other boards' tickets where they may contribute.
     */
    private List<Ticket> loadTicketsWithMemberAccess(User user, UUID organizationId, MilestoneTicketsRequest request) {
        List<UUID> ticketIds = request.ticketIds().stream().distinct().toList();
        List<Ticket> tickets = ticketRepository.findAllById(ticketIds);
        ticketValidator.ensureAllTicketsFound(tickets, ticketIds);

        tickets.stream()
                .map(ticket -> ticket.getBoard().getId())
                .distinct()
                .forEach(ticketBoardId -> boardAccessService.verifyBoardAccess(
                        organizationId, ticketBoardId, user.getId(), BoardMemberRole.MEMBER));
        return tickets;
    }
}
