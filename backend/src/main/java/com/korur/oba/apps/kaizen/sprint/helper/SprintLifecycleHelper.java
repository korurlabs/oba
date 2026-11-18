package com.korur.oba.apps.kaizen.sprint.helper;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.backlog.validator.BacklogValidator;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardSprint;
import com.korur.oba.apps.kaizen.board.repository.BoardSprintRepository;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.sprint.constant.SprintConstant;
import com.korur.oba.apps.kaizen.sprint.constant.SprintExceptionLogConstant;
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kaizen.sprint.constant.processor.SprintRecurrenceProcessorLogConstant;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeriesBoard;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceRunStatus;
import com.korur.oba.apps.kaizen.sprint.enums.SprintStatus;
import com.korur.oba.apps.kaizen.sprint.repository.SprintRepository;
import com.korur.oba.apps.kaizen.sprint.repository.SprintSeriesRepository;
import com.korur.oba.apps.kaizen.sprint.validator.SprintValidator;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.helper.TicketHelper;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.SlugUtil;

/**
 * Holds the rules that create, link, start and complete a sprint.
 *
 * These rules are shared by two callers: the sprint service, which acts for a
 * board admin, and the recurrence job, which acts as the system on sprints an
 * admin switched to repeat automatically. The helper therefore checks no user
 * permission. Every caller must have authorised the change before it calls in,
 * and must have checked the sprint status the transition starts from, because
 * a user gets an error for a wrong status while the job records a run status.
 * No controller may reach this class.
 *
 * It also keeps the slug of every board-sprint link, the sprint's URL segment on
 * that board: every path that creates, restores or renames a link goes through it,
 * so the slug stays unique among the board's live links.
 */
@Component
@RequiredArgsConstructor
public class SprintLifecycleHelper {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SprintLifecycleHelper.class);

    private final SprintRepository sprintRepository;
    private final SprintSeriesRepository sprintSeriesRepository;
    private final BoardSprintRepository boardSprintRepository;
    private final TicketRepository ticketRepository;
    private final SprintValidator sprintValidator;
    private final BacklogValidator backlogValidator;
    private final TicketHelper ticketHelper;
    private final LoggingService loggingService;

    /**
     * Saves a new PLANNED sprint linked to the given boards.
     *
     * The links keep the order of the list. The boards are linked without the
     * relation check of linkSprintToBoardIfNeeded: a user creates a sprint on
     * one board, and the job reuses the boards of a sprint that was linked to
     * them before.
     *
     * @param sprint the unsaved sprint with its name, goal and dates set
     * @param boards the boards to link, at least one
     * @return the saved sprint
     */
    public Sprint createSprint(Sprint sprint, List<Board> boards) {
        return saveNewPlannedSprint(sprint, boards);
    }

    /**
     * Saves the next PLANNED sprint of a series, linked to the given boards as
     * createSprint does. It gets the series' next round and is named
     * "{series name} {round}"; the series' counter then moves on, so a later
     * sprint never gets the same round. The caller holds the series lock.
     *
     * @param series    the locked series
     * @param startDate start of the new sprint
     * @param endDate   end of the new sprint
     * @param boards    the boards to link, at least one
     * @return the saved sprint
     */
    public Sprint createSeriesSprint(SprintSeries series, Instant startDate, Instant endDate, List<Board> boards) {
        int round = series.getNextRound();
        Sprint sprint = Sprint.builder()
                .name(SprintSeriesNaming.sprintName(series.getName(), round))
                .startDate(startDate)
                .endDate(endDate)
                .series(series)
                .round(round)
                .build();
        series.setNextRound(round + 1);
        return saveNewPlannedSprint(sprint, boards);
    }

    /**
     * Sets the PLANNED status, links the boards in list order, each with a slug free on
     * that board, and saves.
     */
    private Sprint saveNewPlannedSprint(Sprint sprint, List<Board> boards) {
        sprint.setStatus(SprintStatus.PLANNED);

        for (Board board : boards) {
            addBoardLink(sprint, board);
        }

        return saveWithSlugs(sprint);
    }

    /**
     * Links a sprint to a board unless it is linked already.
     *
     * A soft-deleted link between the two is restored rather than duplicated.
     * A new link requires the board to be a parent, child or sibling of a board
     * the sprint is already linked to.
     *
     * @param sprint the sprint to link
     * @param board  the board to link it to
     */
    public void linkSprintToBoardIfNeeded(Sprint sprint, Board board) {
        boolean alreadyLinked = sprint.getBoardSprints().stream()
                .anyMatch(bs -> bs.getBoard().getId().equals(board.getId()));
        if (alreadyLinked) {
            return;
        }

        Optional<BoardSprint> deletedBoardSprint = boardSprintRepository
                .findDeletedByBoardIdAndSprintId(board.getId(), sprint.getId());
        if (deletedBoardSprint.isPresent()) {
            restoreBoardLink(sprint, deletedBoardSprint.get());
            return;
        }

        sprintValidator.ensureSprintCanBeLinked(sprint, board);

        addBoardLink(sprint, board);
        saveWithSlugs(sprint);
    }

    /**
     * Picks the slug a sprint with the given name gets on a board: the name's base slug, or
     * the first suffixed form of it that is not a fixed segment of the board pages and that
     * no other live link of the board uses.
     *
     * @param sprintName          the sprint name
     * @param boardId             the board the link belongs to
     * @param ignoreBoardSprintId the link whose own slug does not count as taken, or null for
     *                            a new or restored link
     * @return the free slug
     */
    public String slugForBoard(String sprintName, UUID boardId, UUID ignoreBoardSprintId) {
        String base = SlugUtil.slugify(sprintName, SprintConstant.SLUG_FALLBACK);
        return SlugUtil.firstFree(base, candidate -> SprintConstant.RESERVED_SLUGS.contains(candidate)
                || (ignoreBoardSprintId == null
                        ? boardSprintRepository.existsByBoard_IdAndSlug(boardId, candidate)
                        : boardSprintRepository.existsByBoard_IdAndSlugAndIdNot(boardId, candidate, ignoreBoardSprintId)));
    }

    /**
     * Adds a new, unsaved link between the sprint and the board, with a slug free on that
     * board. The slug is chosen before the link joins the sprint, because the free-slug
     * check flushes pending links and a link must never reach the database without one.
     * The caller saves the sprint, through {@link #saveWithSlugs(Sprint)}.
     *
     * @param sprint the sprint, new or saved
     * @param board  the board to link
     */
    public void addBoardLink(Sprint sprint, Board board) {
        BoardSprint boardSprint = BoardSprint.builder()
                .board(board)
                .sprint(sprint)
                .slug(slugForBoard(sprint.getName(), board.getId(), null))
                .build();
        sprint.getBoardSprints().add(boardSprint);
    }

    /**
     * Makes a soft-deleted link live again with a slug that is free on its board now. The
     * old slug may have been taken while the link was deleted, and the sprint may have been
     * renamed since, so the slug is chosen again from the current name.
     *
     * @param sprint      the live sprint of the link
     * @param deletedLink the soft-deleted link between the sprint and a board
     * @throws ConflictException STA_SLUG_TAKEN when a concurrent write took the chosen slug
     */
    public void restoreBoardLink(Sprint sprint, BoardSprint deletedLink) {
        String slug = slugForBoard(sprint.getName(), deletedLink.getBoard().getId(), null);
        try {
            boardSprintRepository.restoreByIdWithSlug(deletedLink.getId(), slug);
        } catch (DataIntegrityViolationException exception) {
            throw toSlugConflict(exception, sprint);
        }
    }

    /**
     * Makes every soft-deleted link of a restored board live again. A link keeps its slug
     * when no live link of the board uses it and it is not one of
     * {@link SprintConstant#RESERVED_SLUGS}; otherwise it gets the next free slug for its
     * sprint's current name, as a restored board does for its own slug. Links are restored
     * newest first, so of two deleted links with one slug, the one that held it last keeps it.
     *
     * Every deleted link of the board comes back, also one removed before the board was
     * deleted and one whose sprint is deleted; the sprint list of the board leaves out
     * links to deleted sprints.
     *
     * @param boardId the restored board
     * @throws ConflictException STA_SLUG_TAKEN when a concurrent write took a chosen slug
     */
    public void restoreLinksOfBoard(UUID boardId) {
        for (UUID boardSprintId : boardSprintRepository.findDeletedIdsByBoardId(boardId)) {
            String sprintName = boardSprintRepository.findSprintNameByBoardSprintId(boardSprintId);
            String slug = null;
            try {
                if (boardSprintRepository.restoreByIdIfSlugFree(
                        boardSprintId, SprintConstant.RESERVED_SLUGS) == 0) {
                    slug = slugForBoard(sprintName, boardId, null);
                    boardSprintRepository.restoreByIdWithSlug(boardSprintId, slug);
                }
            } catch (DataIntegrityViolationException exception) {
                if (!SlugUtil.isSlugConflict(exception, SprintConstant.SLUG_UNIQUE_INDEX)) {
                    throw exception;
                }
                loggingService.warn(LOG_PREFIX, LogLayer.HELPER,
                        SprintExceptionLogConstant.SPRINT_LINK_SLUG_RACE_LOST, boardSprintId, slug,
                        exception.getMostSpecificCause().getMessage());
                throw new ConflictException(ExceptionDomainKaizenError.STA_SLUG_TAKEN,
                        Map.of(SprintConstant.FIELD_NAME, sprintName));
            }
        }
    }

    /**
     * Brings the slug of each live link in line with the sprint's current name after a
     * rename. A link whose slug still has the name's base slug keeps it, so a change of
     * letter case or punctuation does not move the sprint's URL; any other link gets a new
     * slug free on its board. The caller saves the sprint.
     *
     * @param sprint the sprint, with the new name already applied
     */
    public void syncSlugsWithName(Sprint sprint) {
        String base = SlugUtil.slugify(sprint.getName(), SprintConstant.SLUG_FALLBACK);
        for (BoardSprint boardSprint : sprint.getBoardSprints()) {
            if (!SlugUtil.sameBase(boardSprint.getSlug(), base)) {
                boardSprint.setSlug(slugForBoard(
                        sprint.getName(), boardSprint.getBoard().getId(), boardSprint.getId()));
            }
        }
    }

    /**
     * Writes a sprint and its links at once, so a concurrent write that took the same slug
     * on a board after the free-slug check is caught here, inside the request that chose
     * the slug. Only the slug index is answered with a conflict; every other integrity
     * failure goes up as it is.
     *
     * @param sprint the sprint to save, every link with its slug set
     * @return the saved sprint
     * @throws ConflictException STA_SLUG_TAKEN when the slug index refuses the write
     */
    public Sprint saveWithSlugs(Sprint sprint) {
        try {
            return sprintRepository.saveAndFlush(sprint);
        } catch (DataIntegrityViolationException exception) {
            throw toSlugConflict(exception, sprint);
        }
    }

    /**
     * Returns the boards of the sprint that already have a different ACTIVE sprint.
     *
     * A board may have only one ACTIVE sprint, so the sprint can start, or take
     * over from a closing sprint, only when this list is empty. Each board is
     * checked for every ACTIVE sprint it holds, so a board left with two of them
     * is reported instead of failing the lookup.
     *
     * @param sprint         the sprint whose boards are checked
     * @param ignoreSprintId a sprint that does not count as blocking, such as the
     *                       sprint being closed in the same step; null for none
     * @return the blocking boards in link order, empty when none blocks
     */
    public List<Board> findLinkedBoardsWithOtherActiveSprint(Sprint sprint, UUID ignoreSprintId) {
        return sprint.getBoardSprints().stream()
                .map(BoardSprint::getBoard)
                .filter(board -> sprintRepository
                        .findIdsByBoardIdsAndStatus(List.of(board.getId()), SprintStatus.ACTIVE).stream()
                        .anyMatch(activeId -> !activeId.equals(sprint.getId()) && !activeId.equals(ignoreSprintId)))
                .toList();
    }

    /**
     * Returns the ACTIVE sprints, other than the given sprint, that are linked to
     * any of its boards: the sprints a takeover by it would close. A sprint shared
     * by several of those boards is returned once, and a board holding two ACTIVE
     * sprints yields both.
     *
     * @param sprint the sprint that would take over
     * @return the blocking sprints, empty when none blocks
     */
    public List<Sprint> findOtherActiveSprintsOnLinkedBoards(Sprint sprint) {
        List<UUID> boardIds = sprint.getBoardSprints().stream()
                .map(boardSprint -> boardSprint.getBoard().getId())
                .toList();
        if (boardIds.isEmpty()) {
            return List.of();
        }

        List<UUID> blockingSprintIds = sprintRepository.findIdsByBoardIdsAndStatus(boardIds, SprintStatus.ACTIVE).stream()
                .filter(activeId -> !activeId.equals(sprint.getId()))
                .toList();
        if (blockingSprintIds.isEmpty()) {
            return List.of();
        }
        return sprintRepository.findAllById(blockingSprintIds);
    }

    /**
     * Closes the blocking sprints and starts the taking sprint, so each board ends
     * with one ACTIVE sprint that holds all its unfinished work.
     *
     * For each blocking sprint in a series its series row is locked first, in
     * the order the recurrence job takes its locks, so a job run on that series
     * waits instead of deadlocking. Its unfinished tickets on a board the taking sprint
     * is linked to move into the taking sprint and keep their board, key and
     * status; the others go to the backlog of their own board. DONE tickets stay
     * in the blocking sprint, which is then COMPLETED. The caller has checked the
     * user's role on every board linked to each blocking sprint.
     *
     * @param takingSprint    a saved PLANNED sprint
     * @param blockingSprints the ACTIVE sprints to close, as found by
     *                        {@link #findOtherActiveSprintsOnLinkedBoards(Sprint)}; may be empty
     * @return the started taking sprint and how many sprints and tickets moved
     */
    public TakeoverResult takeOverAndStart(Sprint takingSprint, List<Sprint> blockingSprints) {
        Set<UUID> linkedBoardIds = takingSprint.getBoardSprints().stream()
                .map(boardSprint -> boardSprint.getBoard().getId())
                .collect(Collectors.toSet());

        int movedTicketCount = 0;
        int backlogTicketCount = 0;
        for (Sprint blockingSprint : blockingSprints) {
            if (blockingSprint.getSeries() != null) {
                sprintSeriesRepository.findByIdForUpdate(blockingSprint.getSeries().getId());
            }

            List<Ticket> toTakingSprint = new ArrayList<>();
            List<Ticket> toBacklog = new ArrayList<>();
            for (Ticket ticket : ticketRepository.findBySprint_IdAndStatusNot(blockingSprint.getId(), TicketStatus.DONE)) {
                if (linkedBoardIds.contains(ticket.getBoard().getId())) {
                    toTakingSprint.add(ticket);
                } else {
                    toBacklog.add(ticket);
                }
            }

            moveToEachBoardBacklog(toBacklog, blockingSprint.getId());
            completeSprint(blockingSprint, toTakingSprint,
                    TicketRelocationAction.MOVE_TO_SPRINT, null, takingSprint, null);
            movedTicketCount += toTakingSprint.size();
            backlogTicketCount += toBacklog.size();
        }

        Sprint startedSprint = startSprint(takingSprint);
        return new TakeoverResult(startedSprint, blockingSprints.size(), movedTicketCount, backlogTicketCount);
    }

    /**
     * The outcome of {@link #takeOverAndStart(Sprint, List)}, so the caller can log
     * what the takeover changed.
     *
     * @param sprint             the started taking sprint
     * @param closedSprintCount  the blocking sprints marked COMPLETED
     * @param movedTicketCount   the unfinished tickets moved into the taking sprint
     * @param backlogTicketCount the unfinished tickets moved to the backlog of their board
     */
    public record TakeoverResult(Sprint sprint, int closedSprintCount, int movedTicketCount,
                                 int backlogTicketCount) {
    }

    /**
     * Moves each ticket to the backlog of its own board, since a shared sprint
     * holds tickets of several boards. The backlog rule sets them to TODO.
     *
     * @param tickets        the tickets to move; may be empty
     * @param sourceSprintId the sprint they leave, for logging
     */
    public void moveToEachBoardBacklog(List<Ticket> tickets, UUID sourceSprintId) {
        Map<UUID, List<Ticket>> ticketsByBoardId = tickets.stream()
                .collect(Collectors.groupingBy(ticket -> ticket.getBoard().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<UUID, List<Ticket>> entry : ticketsByBoardId.entrySet()) {
            Backlog backlog = backlogValidator.ensureBacklogExistsByBoardId(entry.getKey());
            ticketHelper.relocateTickets(entry.getValue(), TicketRelocationAction.MOVE_TO_BACKLOG,
                    null, null, backlog, sourceSprintId);
        }
    }

    /**
     * Moves a PLANNED sprint to ACTIVE.
     *
     * @param sprint a PLANNED sprint whose boards have no other ACTIVE sprint
     * @return the saved sprint
     */
    public Sprint startSprint(Sprint sprint) {
        sprint.setStatus(SprintStatus.ACTIVE);
        return sprintRepository.save(sprint);
    }

    /**
     * Relocates the unfinished tickets of an ACTIVE sprint and marks it COMPLETED.
     *
     * DONE tickets stay in the completed sprint. The targets must already be
     * resolved and checked by the caller; see TicketHelper.relocateTickets for
     * what each action needs.
     *
     * @param sprint            an ACTIVE sprint
     * @param unfinishedTickets the sprint's tickets that are not DONE
     * @param action            where the unfinished tickets go
     * @param targetBoard       the target board for a cross-board move, null for the same board
     * @param targetSprint      the target sprint for MOVE_TO_SPRINT, else null
     * @param targetBacklog     the target backlog for MOVE_TO_BACKLOG, else null
     * @return the saved sprint
     */
    public Sprint completeSprint(
            Sprint sprint,
            List<Ticket> unfinishedTickets,
            TicketRelocationAction action,
            Board targetBoard,
            Sprint targetSprint,
            Backlog targetBacklog) {
        if (!unfinishedTickets.isEmpty()) {
            ticketHelper.relocateTickets(
                    unfinishedTickets, action,
                    targetBoard, targetSprint, targetBacklog, sprint.getId());
        }

        sprint.setStatus(SprintStatus.COMPLETED);
        return sprintRepository.save(sprint);
    }

    /**
     * The series' boards that are not deleted, in their link order: the boards
     * the series' next sprint is linked to.
     *
     * @param series the series
     * @return the live boards by position, empty when none is left
     */
    public List<Board> liveBoardsInOrder(SprintSeries series) {
        return series.getBoards().stream()
                .sorted(Comparator.comparingInt(SprintSeriesBoard::getPosition))
                .map(SprintSeriesBoard::getBoard)
                .filter(board -> board != null && !board.isDeleted())
                .toList();
    }

    /**
     * Switches the series off with a FAILED run, for a series that can no
     * longer create or start its next sprint. The caller holds the series lock.
     *
     * @param series  the locked series
     * @param now     the time of the run
     * @param message why the series stopped
     */
    public void switchOff(SprintSeries series, Instant now, String message) {
        series.setEnabled(false);
        series.setNextCreationAt(null);
        recordRun(series, now, SprintRecurrenceRunStatus.FAILED, message);
    }

    /**
     * Records BLOCKED with the names of the boards that hold another ACTIVE
     * sprint. A retry that is blocked the same way writes nothing, so the series
     * keeps the time the block was first seen. The caller holds the series lock.
     *
     * @param series         the locked series
     * @param sprint         the sprint that could not start or roll over
     * @param blockingBoards the boards with another ACTIVE sprint, at least one
     * @param now            the time of the run
     */
    public void recordBlocked(SprintSeries series, Sprint sprint, List<Board> blockingBoards, Instant now) {
        String boardNames = blockingBoards.stream()
                .map(Board::getName)
                .collect(Collectors.joining(SprintRecurrenceConstant.RUN_MESSAGE_BOARD_SEPARATOR));
        String message = truncate(String.format(SprintRecurrenceConstant.RUN_MESSAGE_BLOCKED_FORMAT, boardNames));

        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                SprintRecurrenceProcessorLogConstant.SPRINT_RECURRENCE_BLOCKED, sprint.getId(), boardNames);

        if (series.getLastRunStatus() == SprintRecurrenceRunStatus.BLOCKED
                && message.equals(series.getLastRunMessage())) {
            return;
        }
        recordRun(series, now, SprintRecurrenceRunStatus.BLOCKED, message);
    }

    /**
     * Stores the outcome of a run on the series, the message cut to the column
     * length, and saves it. The caller holds the series lock.
     *
     * @param series  the locked series
     * @param now     the time of the run
     * @param status  the outcome
     * @param message the detail shown to admins, or null
     */
    public void recordRun(SprintSeries series, Instant now, SprintRecurrenceRunStatus status, String message) {
        series.setLastRunAt(now);
        series.setLastRunStatus(status);
        series.setLastRunMessage(message == null ? null : truncate(message));
        sprintSeriesRepository.save(series);
    }

    private static String truncate(String message) {
        if (message.length() <= SprintRecurrenceConstant.LAST_RUN_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, SprintRecurrenceConstant.LAST_RUN_MESSAGE_MAX_LENGTH);
    }

    /**
     * Turns a violation of the slug index into the conflict the client can retry, after
     * logging the lost race; the conflict names the sprint name the slug was built from.
     * Any other integrity failure is returned unchanged for the caller to throw.
     */
    private RuntimeException toSlugConflict(DataIntegrityViolationException exception, Sprint sprint) {
        if (!SlugUtil.isSlugConflict(exception, SprintConstant.SLUG_UNIQUE_INDEX)) {
            return exception;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.HELPER,
                SprintExceptionLogConstant.SPRINT_SLUG_RACE_LOST, sprint.getId(),
                exception.getMostSpecificCause().getMessage());
        return new ConflictException(ExceptionDomainKaizenError.STA_SLUG_TAKEN,
                Map.of(SprintConstant.FIELD_NAME, sprint.getName()));
    }
}
