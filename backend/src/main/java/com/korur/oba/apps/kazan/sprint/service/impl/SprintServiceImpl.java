package com.korur.oba.apps.kazan.sprint.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.apps.kazan.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.entity.BoardSprint;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.repository.BoardSprintRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.sprint.constant.SprintConstant;
import com.korur.oba.apps.kazan.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kazan.sprint.constant.service.SprintServiceLogConstant;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintBulkLinkRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCompleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCreateRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintDeleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintStartRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUnlinkRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUnlinkTransferRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUpdateRequest;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kazan.sprint.helper.SprintSeriesNaming;
import com.korur.oba.apps.kazan.sprint.mapper.SprintMapper;
import com.korur.oba.apps.kazan.sprint.repository.SprintRepository;
import com.korur.oba.apps.kazan.sprint.service.SprintSeriesService;
import com.korur.oba.apps.kazan.sprint.service.SprintService;
import com.korur.oba.apps.kazan.sprint.validator.SprintValidator;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.apps.kazan.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kazan.ticket.enums.TicketStatus;
import com.korur.oba.apps.kazan.ticket.helper.TicketHelper;
import com.korur.oba.apps.kazan.ticket.repository.TicketRepository;
import com.korur.oba.apps.kazan.ticket.validator.TicketValidator;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Implementation of sprint write operations.
 * <p>
 * Manages sprint lifecycle including creation, starting, completing,
 * and moving incomplete tickets to backlog on completion.
 */
@Service
@RequiredArgsConstructor
public class SprintServiceImpl implements SprintService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SprintServiceImpl.class);

    private final SprintRepository sprintRepository;
    private final TicketRepository ticketRepository;
    private final BoardSprintRepository boardSprintRepository;
    private final BoardAccessService boardAccessService;
    private final SprintValidator sprintValidator;
    private final BacklogValidator backlogValidator;
    private final TicketValidator ticketValidator;
    private final SprintMapper sprintMapper;
    private final TicketHelper ticketHelper;
    private final SprintLifecycleHelper sprintLifecycleHelper;
    private final SprintSeriesService sprintSeriesService;
    private final LoggingService loggingService;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    @Transactional
    public SprintDetailResponse createSprint(
            User user,
            UUID organizationId,
            UUID boardId,
            SprintCreateRequest request) {
        // Validate board exists and user has admin permissions.
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        // The path board comes first, then each child board the user may share the sprint with.
        List<Board> boards = new ArrayList<>();
        boards.add(board);
        boards.addAll(resolveChildBoards(user, organizationId, board, request.childBoardIds()));

        // With repeat on, the typed name is the series name and the sprint is its first
        // round, named before it is saved so its slugs come from "{name} 1".
        Sprint newSprint = sprintMapper.toEntity(request);
        boolean repeats = request.series() != null && Boolean.TRUE.equals(request.series().enabled());
        String seriesName = null;
        if (repeats) {
            seriesName = request.name().trim();
            sprintValidator.ensureSeriesNameValid(seriesName, null);
            newSprint.setName(SprintSeriesNaming.sprintName(seriesName, SprintRecurrenceConstant.SERIES_FIRST_ROUND));
        }

        // Status and the board links are set by the shared create rule.
        Sprint sprint = sprintLifecycleHelper.createSprint(newSprint, boards);

        // Start the series before mapping, so the response carries it.
        sprintSeriesService.applyOnCreate(sprint, seriesName, request.series());

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_CREATED, sprint.getId());
        if (boards.size() > 1) {
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    SprintServiceLogConstant.SPRINT_CREATED_WITH_CHILD_BOARDS, sprint.getId(), boards.size() - 1);
        }

        // A takeover closes the active sprints of the linked boards and starts the new
        // sprint in the same transaction, also when nothing blocks any more.
        if (Boolean.TRUE.equals(request.takeOver())) {
            sprint = takeOver(user, organizationId, sprint);
        }

        return sprintMapper.toDetailResponse(sprint);
    }

    @Override
    @Transactional
    public SprintDetailResponse updateSprint(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID sprintId,
            SprintUpdateRequest request) {
        // Validate board and sprint exist, and user has admin permissions.
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN);

        // Lock the series row before the sprint row is written. The job holds
        // that row while it writes the series' sprints, so taking the two locks in
        // the job's order means a date change or a rename and a rollover wait for
        // each other instead of deadlocking. A sprint the job changed meanwhile then
        // fails its version check and answers 409, rather than writing back old values.
        sprintSeriesService.lockSeries(sprint);

        // A sprint of an enabled series is named after the series, so it is renamed
        // only through the series name, which renames every unfinished sprint of it.
        sprintValidator.ensureSeriesNaming(sprint, request);

        // Apply partial update via mapper; a new name moves the slug on each board
        // where the name's slug changed.
        sprintMapper.updateEntity(sprint, request);
        if (request.seriesName() != null) {
            sprintSeriesService.renameSeries(sprint, request.seriesName());
        }
        sprintLifecycleHelper.syncSlugsWithName(sprint);
        sprint = sprintLifecycleHelper.saveWithSlugs(sprint);

        // New dates move the rollover, so a repeating sprint's schedule is checked and recomputed.
        if (request.startDate() != null || request.endDate() != null) {
            sprintSeriesService.onSprintDatesChanged(sprint);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_UPDATED, sprint.getId());

        return sprintMapper.toDetailResponse(sprint);
    }

    @Override
    @Transactional
    public void deleteSprint(User user, UUID organizationId, UUID boardId, UUID sprintId, SprintDeleteRequest request) {
        // Validate board and sprint exist, the sprint is on the board, and user has admin permissions.
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN);

        // A sprint of a series is written only under the series lock, in the job's order.
        sprintSeriesService.lockSeries(sprint);

        // Fetch tickets belonging to this sprint.
        List<Ticket> sprintTickets = ticketRepository.findBySprint_Id(sprintId);

        // If the sprint has tickets, a ticket action is required.
        if (!sprintTickets.isEmpty()) {
            ticketValidator.ensureTicketActionProvided(request.ticketAction(), sprintId);

            // Validate and resolve entities at the service layer before delegating to
            // helper.
            Backlog backlog = null;
            Sprint targetSprint = null;

            switch (request.ticketAction()) {
                case MOVE_TO_BACKLOG -> backlog = backlogValidator.ensureBacklogExistsByBoardId(boardId);
                case MOVE_TO_SPRINT -> {
                    ticketValidator.ensureTargetSprintProvided(request.targetSprintId(), sprintId);
                    sprintValidator.ensureTargetSprintNotSelf(request.targetSprintId(), sprintId);
                    targetSprint = sprintValidator.ensureSprintExists(request.targetSprintId());
                    sprintValidator.ensureSprintBelongsToBoard(targetSprint, boardId);
                }
                case DELETE -> { /* no pre-validation needed */ }
            }

            ticketHelper.relocateTickets(
                    sprintTickets, request.ticketAction(),
                    null, targetSprint, backlog, sprintId);
        }

        // Soft-delete the sprint. BoardSprints cascade via REMOVE.
        sprintRepository.delete(sprint);

        // A deleted sprint created ahead is created again by the job, at its creation time.
        sprintSeriesService.rescheduleSeries(sprint);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_DELETED, sprintId);
    }

    @Override
    @Transactional
    public void restoreSprint(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID sprintId) {
        // Validate board exists and user has admin permissions.
        boardAccessService.verifyBoardAccess(organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);

        // Find the sprint including soft-deleted records.
        Sprint sprint = sprintRepository.findByIdWithDeleted(sprintId)
                .orElseThrow(() -> new NotFoundException(
                        ExceptionDomainKazanError.NOT_SPRINT_FOUND,
                        Map.of(SprintConstant.FIELD_SPRINT_ID, sprintId)));

        // Deleting a sprint soft-deletes its links, so the path board is matched against
        // deleted links too: a board restores only a sprint it held.
        sprintValidator.ensureSprintWasOnBoard(sprint, boardId);

        // Ensure the sprint is actually deleted.
        sprintValidator.ensureSprintIsDeleted(sprint);

        // Restore the sprint. Tickets and boardSprints were handled before
        // deletion (moved/deleted separately), so they are not cascade-restored.
        sprintRepository.restoreById(sprintId);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_RESTORED, sprintId);
    }

    @Override
    @Transactional
    public SprintDetailResponse startSprint(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID sprintId,
            SprintStartRequest request) {
        // Validate board and sprint exist, and user has admin permissions.
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN);

        // Sprint must be in PLANNED status to start.
        sprintValidator.ensureSprintIsPlanned(sprint);

        // A sprint of a series is written only under the series lock, in the job's order.
        sprintSeriesService.lockSeries(sprint);

        // A missing body equals takeover off.
        if (request != null && Boolean.TRUE.equals(request.takeOver())) {
            sprint = takeOver(user, organizationId, sprint);
        } else {
            // A shared sprint must not leave any of its boards with two ACTIVE sprints,
            // so every linked board is checked, not only the path board.
            sprintValidator.ensureNoBlockingBoards(sprintId,
                    sprintLifecycleHelper.findLinkedBoardsWithOtherActiveSprint(sprint, null));
            sprint = sprintLifecycleHelper.startSprint(sprint);

            // The started sprint is now the one the series creates its next sprint from.
            sprintSeriesService.rescheduleSeries(sprint);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_STARTED, sprint.getId());

        return sprintMapper.toDetailResponse(sprint);
    }

    @Override
    @Transactional
    public SprintDetailResponse completeSprint(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID sprintId,
            SprintCompleteRequest request) {
        // Validate board and sprint exist, and user has admin permissions.
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN);

        // Sprint must be in ACTIVE status to complete.
        sprintValidator.ensureSprintIsActive(sprint);

        // A sprint of a series is written only under the series lock, in the job's order.
        // The lock may have waited for a job run that completed this sprint, so it
        // is read again and must still be ACTIVE: a series never advances twice.
        Optional<SprintSeries> series = sprintSeriesService.lockSeries(sprint);
        if (series.isPresent()) {
            entityManager.refresh(sprint);
            sprintValidator.ensureSprintIsActive(sprint);
        }

        boolean moveToNextSprint = Boolean.TRUE.equals(request.moveToNextSprint());
        sprintValidator.ensureMoveToNextSprintAllowed(sprint, request,
                series.isPresent() && !sprintLifecycleHelper.liveBoardsInOrder(series.get()).isEmpty());

        // Find incomplete tickets (status != DONE).
        List<Ticket> incompleteTickets = ticketRepository.findBySprint_IdAndStatusNot(
                sprintId, TicketStatus.DONE);

        // Resolve the relocation targets of the user's chosen action. The next sprint
        // of the series is the target of moveToNextSprint and does not exist yet.
        Board targetBoard = null;
        Backlog targetBacklog = null;
        Sprint targetSprint = null;
        if (!incompleteTickets.isEmpty() && !moveToNextSprint) {

            // Resolve the target board if cross-board relocation is requested.
            if (request.targetBoardId() != null) {
                targetBoard = boardAccessService.verifyBoardAccess(
                        organizationId, request.targetBoardId(), user.getId(), BoardMemberRole.ADMIN);
            }

            UUID effectiveBoardId = targetBoard != null ? targetBoard.getId() : boardId;

            switch (request.ticketAction()) {
                case MOVE_TO_BACKLOG -> targetBacklog = backlogValidator.ensureBacklogExistsByBoardId(effectiveBoardId);
                case MOVE_TO_SPRINT -> {
                    ticketValidator.ensureTargetSprintProvided(request.targetSprintId(), sprintId);
                    sprintValidator.ensureTargetSprintNotSelf(request.targetSprintId(), sprintId);
                    targetSprint = sprintValidator.ensureSprintExists(request.targetSprintId());
                    sprintValidator.ensureSprintBelongsToBoard(targetSprint, effectiveBoardId);
                }
                case DELETE -> { /* no pre-validation needed */ }
            }
        }

        // A series that repeats moves on to its next round now rather than at the old
        // end date. The round is prepared before the complete, as the job does, so
        // the unfinished tickets can move into it; with no target board they keep
        // their own boards.
        Instant now = clock.instant();
        Optional<Sprint> nextSprint = sprintSeriesService.advanceOnComplete(sprint, now);
        if (moveToNextSprint) {
            // The validator required an enabled series with a live board, so a round exists.
            targetSprint = nextSprint.orElseThrow();
        }

        // Relocate incomplete tickets and transition to COMPLETED status.
        sprint = sprintLifecycleHelper.completeSprint(
                sprint, incompleteTickets, request.ticketAction(),
                targetBoard, targetSprint, targetBacklog);

        nextSprint.ifPresent(next -> sprintSeriesService.startAdvancedSprint(next, now));

        // Closed early, the sprint no longer drives the series' schedule: the next
        // round does, or nothing when the series is off.
        sprintSeriesService.rescheduleSeries(sprint);

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_COMPLETED, sprint.getId());

        return sprintMapper.toDetailResponse(sprint);
    }

    @Override
    @Transactional
    public SprintDetailResponse linkSprint(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID sprintId) {
        // Validate board exists and user has admin permissions.
        // Sprint not yet linked to board, so verifySprintAccess would fail.
        final Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        // The sprint must still belong to the board's organization, so no tenant links
        // another tenant's sprint by its id.
        final Sprint sprint = sprintValidator.ensureSprintExistsInOrganization(organizationId, sprintId);

        // The series row is locked before the sprint's links are written, in the job's order.
        sprintSeriesService.lockSeries(sprint);

        // Check for soft-deleted BoardSprint link and restore instead of creating new.
        Optional<BoardSprint> deletedBoardSprint = boardSprintRepository.findDeletedByBoardIdAndSprintId(boardId,
                sprintId);
        if (deletedBoardSprint.isPresent()) {
            sprintLifecycleHelper.restoreBoardLink(sprint, deletedBoardSprint.get());
            sprintSeriesService.onBoardLinked(sprint, board);

            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    SprintServiceLogConstant.SPRINT_BOARD_LINK_RESTORED, sprintId, boardId);
            return sprintMapper.toDetailResponse(sprint);
        }

        // Ensure the sprint can be linked to the board.
        sprintValidator.ensureSprintCanBeLinked(sprint, board);

        // Create the BoardSprint join entity linking both sides, with a slug free on the board.
        // The link is written before the series claims the board: the claim flushes too, and a
        // slug taken meanwhile must answer as a slug conflict, not as a raw integrity failure.
        sprintLifecycleHelper.addBoardLink(sprint, board);
        Sprint savedSprint = sprintLifecycleHelper.saveWithSlugs(sprint);
        sprintSeriesService.onBoardLinked(savedSprint, board);

        return sprintMapper.toDetailResponse(savedSprint);
    }

    @Override
    @Transactional
    public SprintDetailResponse bulkLinkSprint(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID sprintId,
            SprintBulkLinkRequest request) {
        // Validate the calling board context, that the sprint is on it, and user permissions.
        // The sprint is loaded once for the entire operation.
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN);

        // Lock the series row before any link is written, in the job's order.
        sprintSeriesService.lockSeries(sprint);

        // Process each requested board within the same transactional boundary.
        for (UUID targetBoardId : request.boardIds()) {
            // Verify the user has admin permission on each target board.
            Board targetBoard = boardAccessService.verifyBoardAccess(
                    organizationId, targetBoardId, user.getId(), BoardMemberRole.ADMIN);

            // Skip silently if the board is already actively linked to the sprint.
            boolean alreadyLinked = sprint.getBoardSprints().stream()
                    .anyMatch(bs -> bs.getBoard().getId().equals(targetBoardId));
            if (alreadyLinked) {
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                        SprintServiceLogConstant.SPRINT_BOARD_LINK_SKIPPED, sprintId, targetBoardId);
                continue;
            }

            // Restore a soft-deleted link instead of creating a duplicate row.
            Optional<BoardSprint> deletedBoardSprint =
                    boardSprintRepository.findDeletedByBoardIdAndSprintId(targetBoardId, sprintId);
            if (deletedBoardSprint.isPresent()) {
                sprintLifecycleHelper.restoreBoardLink(sprint, deletedBoardSprint.get());
                sprintSeriesService.onBoardLinked(sprint, targetBoard);
                loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                        SprintServiceLogConstant.SPRINT_BOARD_LINK_RESTORED, sprintId, targetBoardId);
                continue;
            }

            // Validate that the target board is parent, child, or sibling of an
            // already linked board (re-evaluated each iteration so newly-added
            // boards extend the linkable set for subsequent iterations).
            sprintValidator.ensureSprintCanBeLinked(sprint, targetBoard);

            // Create and attach the new BoardSprint join entity, with a slug free on the board,
            // and write it before the series claims the board: the claim flushes too, and a
            // slug taken meanwhile must answer as a slug conflict, not as a raw integrity failure.
            sprintLifecycleHelper.addBoardLink(sprint, targetBoard);
            sprint = sprintLifecycleHelper.saveWithSlugs(sprint);
            sprintSeriesService.onBoardLinked(sprint, targetBoard);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_BULK_LINKED, sprintId, request.boardIds().size());

        return sprintMapper.toDetailResponse(sprint);
    }

    @Override
    @Transactional
    public void unlinkSprint(
            User user,
            UUID organizationId,
            UUID boardId,
            UUID sprintId,
            SprintUnlinkRequest request) {
        // Validate board and sprint exist, the sprint is on the board, and user has admin
        // permissions. The board entity is needed to remove its side of the link.
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.ADMIN);
        Sprint sprint = boardAccessService.verifySprintAccess(
                organizationId, boardId, sprintId, user.getId(), BoardMemberRole.ADMIN);

        // The series row is locked before the sprint is written, in the job's order.
        sprintSeriesService.lockSeries(sprint);

        // Manage tickets associated with the sprint via centralized relocation.
        List<Ticket> sprintTickets = ticketRepository.findBySprint_Id(sprintId);
        if (!sprintTickets.isEmpty()) {
            if (Boolean.TRUE.equals(request.deleteTickets())) {
                ticketHelper.relocateTickets(
                        sprintTickets, TicketRelocationAction.DELETE,
                        null, null, null, sprintId);
            } else if (Objects.nonNull(request.transfer())) {
                SprintUnlinkTransferRequest transfer = request.transfer();

                // Resolve target board (may be cross-board).
                Board targetBoard = null;
                if (!transfer.boardId().equals(boardId)) {
                    targetBoard = boardAccessService.verifyBoardAccess(
                            organizationId, transfer.boardId(), user.getId(), BoardMemberRole.ADMIN);
                }

                UUID effectiveBoardId = targetBoard != null ? targetBoard.getId() : boardId;

                if (Objects.nonNull(transfer.sprintId())) {
                    Sprint targetSprint = sprintValidator.ensureSprintExists(transfer.sprintId());
                    sprintValidator.ensureSprintBelongsToBoard(targetSprint, effectiveBoardId);
                    ticketHelper.relocateTickets(
                            sprintTickets, TicketRelocationAction.MOVE_TO_SPRINT,
                            targetBoard, targetSprint, null, sprintId);
                } else {
                    Backlog targetBacklog = backlogValidator.ensureBacklogExistsByBoardId(effectiveBoardId);
                    ticketHelper.relocateTickets(
                            sprintTickets, TicketRelocationAction.MOVE_TO_BACKLOG,
                            targetBoard, null, targetBacklog, sprintId);
                }
            }
        }

        // Remove the BoardSprint link between the board and the sprint.
        board.getBoardSprints().removeIf(bs -> bs.getSprint().getId().equals(sprint.getId()));
        sprint.getBoardSprints().removeIf(bs -> bs.getBoard().getId().equals(board.getId()));

        // A board unlinked from an unfinished sprint of a series leaves the series, so
        // its later sprints are not linked to the board again.
        sprintSeriesService.onBoardUnlinked(sprint, board.getId());

        if (sprint.getBoardSprints().isEmpty()) {
            // If the sprint has no boards, delete it.
            sprintRepository.delete(sprint);
        } else {
            sprintRepository.saveAndFlush(sprint);
        }
    }

    @Override
    @Transactional
    public void linkSprintToBoardIfNeeded(Sprint sprint, Board board) {
        // Checked here rather than left to callers, so a sprint of another organization is
        // never linked to the board whatever the caller resolved it from.
        sprintValidator.ensureSprintInOrganization(sprint, board.getOrganizationId());
        sprintLifecycleHelper.linkSprintToBoardIfNeeded(sprint, board);
    }

    /**
     * Loads the child boards that share a new sprint, in request order. Each board must be
     * one the user is ADMIN or OWNER of and must sit below the path board. Duplicates and
     * the path board itself are skipped, so a client may send its whole selection as is.
     * Any refusal throws inside the create transaction, so no sprint and no link remain.
     */
    private List<Board> resolveChildBoards(User user, UUID organizationId, Board pathBoard, List<UUID> childBoardIds) {
        if (childBoardIds == null || childBoardIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> distinctIds = new LinkedHashSet<>(childBoardIds);
        distinctIds.remove(pathBoard.getId());

        List<Board> childBoards = new ArrayList<>();
        for (UUID childBoardId : distinctIds) {
            Board childBoard = boardAccessService.verifyBoardAccess(
                    organizationId, childBoardId, user.getId(), BoardMemberRole.ADMIN);
            sprintValidator.ensureBoardIsDescendant(pathBoard, childBoard);
            childBoards.add(childBoard);
        }
        return childBoards;
    }

    /**
     * Closes every ACTIVE sprint on the sprint's linked boards, moves their unfinished
     * tickets into the sprint or to their own board's backlog, and starts the sprint.
     * Closing a sprint changes every board it is linked to, also boards the taking
     * sprint is not linked to, so the user must be ADMIN or OWNER of each of those
     * boards. All roles are checked before the first write, so a refusal changes nothing.
     */
    private Sprint takeOver(User user, UUID organizationId, Sprint sprint) {
        List<Sprint> blockingSprints = sprintLifecycleHelper.findOtherActiveSprintsOnLinkedBoards(sprint);

        Set<UUID> affectedBoardIds = new LinkedHashSet<>();
        for (Sprint blockingSprint : blockingSprints) {
            for (BoardSprint boardSprint : blockingSprint.getBoardSprints()) {
                affectedBoardIds.add(boardSprint.getBoard().getId());
            }
        }
        for (UUID affectedBoardId : affectedBoardIds) {
            boardAccessService.verifyBoardAccess(organizationId, affectedBoardId, user.getId(), BoardMemberRole.ADMIN);
        }

        SprintLifecycleHelper.TakeoverResult result =
                sprintLifecycleHelper.takeOverAndStart(sprint, blockingSprints);

        // Every series whose sprint started or closed here schedules from its new state.
        sprintSeriesService.rescheduleSeries(result.sprint());
        for (Sprint blockingSprint : blockingSprints) {
            sprintSeriesService.rescheduleSeries(blockingSprint);
        }

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                SprintServiceLogConstant.SPRINT_TAKEOVER_COMPLETED, result.sprint().getId(),
                result.closedSprintCount(), result.movedTicketCount(), result.backlogTicketCount());

        return result.sprint();
    }
}
