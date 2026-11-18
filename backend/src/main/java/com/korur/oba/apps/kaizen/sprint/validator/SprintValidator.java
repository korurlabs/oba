package com.korur.oba.apps.kaizen.sprint.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.entity.BoardSprint;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.sprint.constant.SprintConstant;
import com.korur.oba.apps.kaizen.sprint.constant.SprintExceptionLogConstant;
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;
import com.korur.oba.apps.kaizen.sprint.dto.request.SprintCompleteRequest;
import com.korur.oba.apps.kaizen.sprint.dto.request.SprintUpdateRequest;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeriesBoard;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kaizen.sprint.enums.SprintStatus;
import com.korur.oba.apps.kaizen.sprint.repository.SprintRepository;
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for sprint lookups.
 */
@Component
@RequiredArgsConstructor
public class SprintValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(SprintValidator.class);

    private final SprintRepository sprintRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the sprint exists and returns it.
     */
    public Sprint ensureSprintExists(UUID sprintId) {
        // Fetch the sprint or raise a domain-specific not-found error.
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> {
                    // Log for observability before throwing the exception.
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            SprintExceptionLogConstant.SPRINT_NOT_FOUND,
                            sprintId);
                    // Propagate a NotFoundException with field context.
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_SPRINT_FOUND,
                            Map.of(SprintConstant.FIELD_SPRINT_ID, sprintId));
                });
    }

    /**
     * Ensures the sprint exists and belongs to the organization, and returns it. A sprint
     * carries no organization of its own: it belongs to the organization of the boards it is
     * linked to. A sprint of another organization answers the same not found as a missing
     * one, so its id reveals nothing to another tenant.
     *
     * @param organizationId the organization of the request
     * @param sprintId       the sprint identifier
     * @return the sprint
     * @throws NotFoundException if the sprint is missing or belongs to another organization
     */
    public Sprint ensureSprintExistsInOrganization(UUID organizationId, UUID sprintId) {
        Sprint sprint = ensureSprintExists(sprintId);
        ensureSprintInOrganization(sprint, organizationId);
        return sprint;
    }

    /**
     * Ensures every board the sprint was ever linked to belongs to the organization. Links
     * are only made between boards of one hierarchy, which never crosses organizations, so
     * this keeps a sprint id of one tenant from being linked to, or placed on, a board of
     * another.
     *
     * @param sprint         the sprint to check
     * @param organizationId the organization the sprint must belong to
     * @throws NotFoundException if the sprint belongs to another organization or has no link
     */
    public void ensureSprintInOrganization(Sprint sprint, UUID organizationId) {
        if (!sprintRepository.isLinkedOnlyToOrganization(sprint.getId(), organizationId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_OF_OTHER_ORGANIZATION,
                    sprint.getId(), organizationId);
            throw new NotFoundException(
                    ExceptionDomainKaizenError.NOT_SPRINT_FOUND,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId()));
        }
    }

    /**
     * Ensures the sprint has, or had before it was deleted, a link to the board. A restore
     * from a board must not reach a sprint that board never held.
     *
     * @param sprint  the sprint, which may be soft-deleted
     * @param boardId the board of the request path
     * @throws NotFoundException if the sprint was never linked to the board
     */
    public void ensureSprintWasOnBoard(Sprint sprint, UUID boardId) {
        if (!sprintRepository.existsLinkWithDeleted(sprint.getId(), boardId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_NOT_ON_BOARD,
                    sprint.getId(), boardId);
            throw new NotFoundException(
                    ExceptionDomainKaizenError.NOT_SPRINT_FOUND,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId()));
        }
    }

    /**
     * Ensures the sprint can be linked to the board.
     */
    public void ensureSprintCanBeLinked(Sprint sprint, Board board) {
        // Extract boards from the BoardSprint join entities.
        List<Board> sprintBoards = sprint.getBoardSprints().stream()
                .map(BoardSprint::getBoard)
                .toList();

        // Check if the sprint has any boards. If it has no boards, it can be linked.
        if (sprintBoards.isEmpty()) {
            return;
        }

        // Check if the sprint is already linked to the board.
        if (sprintBoards.contains(board)) {
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_ALREADY_LINKED,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId(),
                            SprintConstant.FIELD_BOARD_ID, board.getId()));
        }

        // Check if the provided board has any relation with the sprint's boards.
        // The provided board must be a children, parent or sibling board.

        boolean canBeLinked = false;
        for (Board sprintBoard : sprintBoards) {
            if (sprintBoard.getChildBoards().contains(board)) {
                // Provided board is a child of the sprint board.
                canBeLinked = true;
                break;
            }

            if (sprintBoard.getParentBoard() == null) {
                // Current sprint board is the root board. No need to check further.
                continue;
            }

            if (sprintBoard.getParentBoard().getId().equals(board.getId())) {
                // Provided board is a parent of the current sprint board.
                canBeLinked = true;
                break;
            }

            if (board.getParentBoard() == null) {
                // Provided board is the root board. No need to check further.
                continue;
            }

            if (sprintBoard.getParentBoard().getId().equals(board.getParentBoard().getId())) {
                // Provided board is a sibling of the current sprint board.
                // They share the same parent board.
                canBeLinked = true;
                break;
            }
        }

        if (!canBeLinked) {
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_NOT_LINKABLE,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId(),
                            SprintConstant.FIELD_BOARD_ID, board.getId()));
        }

    }

    /**
     * Ensures the candidate board sits below the root board in the board hierarchy, at any
     * depth. A sprint created on a board may be shared only with that board's descendants,
     * so a sibling, a parent or an unrelated board is refused. The root itself is not its
     * own descendant.
     *
     * @param root      the board the sprint is created on
     * @param candidate the board sent to share the sprint
     * @throws ValidationException if the root is not an ancestor of the candidate
     */
    public void ensureBoardIsDescendant(Board root, Board candidate) {
        // Walk up the parent chain. The visited set stops the walk on a cyclic hierarchy.
        Set<UUID> visited = new HashSet<>();
        Board ancestor = candidate.getParentBoard();
        while (ancestor != null && visited.add(ancestor.getId())) {
            if (ancestor.getId().equals(root.getId())) {
                return;
            }
            ancestor = ancestor.getParentBoard();
        }

        loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                SprintExceptionLogConstant.SPRINT_BOARD_NOT_DESCENDANT,
                root.getId(), candidate.getId());
        throw new ValidationException(
                ExceptionDomainKaizenError.VAL_SPRINT_NOT_LINKABLE,
                Map.of(SprintConstant.FIELD_ROOT_BOARD_ID, root.getId(),
                        SprintConstant.FIELD_BOARD_ID, candidate.getId()));
    }

    /**
     * Ensures the sprint is in PLANNED status.
     *
     * @param sprint the sprint to validate
     * @throws ValidationException if the sprint is not in PLANNED status
     */
    public void ensureSprintIsPlanned(Sprint sprint) {
        if (sprint.getStatus() != SprintStatus.PLANNED) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_INVALID_STATUS_TRANSITION,
                    sprint.getId(), sprint.getStatus(), SprintStatus.ACTIVE);
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_NOT_PLANNED,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId(),
                            SprintConstant.FIELD_STATUS, sprint.getStatus()));
        }
    }

    /**
     * Ensures the sprint is in ACTIVE status.
     *
     * @param sprint the sprint to validate
     * @throws ValidationException if the sprint is not in ACTIVE status
     */
    public void ensureSprintIsActive(Sprint sprint) {
        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_INVALID_STATUS_TRANSITION,
                    sprint.getId(), sprint.getStatus(), SprintStatus.COMPLETED);
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_NOT_ACTIVE,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId(),
                            SprintConstant.FIELD_STATUS, sprint.getStatus()));
        }
    }

    /**
     * Ensures none of a sprint's linked boards has another ACTIVE sprint, so a
     * start never leaves a board with two. The conflict names the blocking boards
     * so the user knows which sprints to close first.
     *
     * @param sprintId       the sprint to start
     * @param blockingBoards the linked boards that hold another ACTIVE sprint
     * @throws ConflictException STA_ACTIVE_SPRINT_EXISTS when the list is not empty
     */
    public void ensureNoBlockingBoards(UUID sprintId, List<Board> blockingBoards) {
        if (blockingBoards.isEmpty()) {
            return;
        }
        List<UUID> boardIds = blockingBoards.stream()
                .map(Board::getId)
                .toList();
        loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                SprintExceptionLogConstant.SPRINT_START_BLOCKED_BY_ACTIVE_SPRINTS,
                sprintId, boardIds);
        String boardNames = blockingBoards.stream()
                .map(Board::getName)
                .collect(Collectors.joining(SprintConstant.BOARD_NAMES_SEPARATOR));
        throw new ConflictException(
                ExceptionDomainKaizenError.STA_ACTIVE_SPRINT_EXISTS,
                Map.of(SprintConstant.FIELD_SPRINT_ID, sprintId,
                        SprintConstant.FIELD_BOARD_NAMES, boardNames));
    }

    /**
     * Ensures the sprint is soft-deleted (for restore operations).
     *
     * @param sprint the sprint to validate
     * @throws ValidationException if the sprint is not deleted
     */
    public void ensureSprintIsDeleted(Sprint sprint) {
        if (!sprint.isDeleted()) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_DELETED_NOT_FOUND,
                    sprint.getId());
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_NOT_DELETED,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId()));
        }
    }

    /**
     * Ensures the target sprint is not the same as the source sprint.
     *
     * @param targetSprintId the target sprint identifier
     * @param sourceSprintId the source sprint identifier
     * @throws ValidationException if the target sprint is the same as the source
     */
    public void ensureTargetSprintNotSelf(UUID targetSprintId, UUID sourceSprintId) {
        if (targetSprintId.equals(sourceSprintId)) {
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_TARGET_SPRINT_SELF_REFERENCE,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sourceSprintId));
        }
    }

    /**
     * Ensures the sprint belongs to the specified board via a BoardSprint link.
     *
     * @param sprint  the sprint to validate
     * @param boardId the board identifier
     * @throws ValidationException if the sprint does not belong to the board
     */
    public void ensureSprintBelongsToBoard(Sprint sprint, UUID boardId) {
        boolean belongsToBoard = sprint.getBoardSprints().stream()
                .anyMatch(bs -> bs.getBoard().getId().equals(boardId));
        if (!belongsToBoard) {
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_TARGET_SPRINT_SAME_BOARD,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId(),
                            SprintConstant.FIELD_BOARD_ID, boardId));
        }
    }

    /**
     * Ensures repetition is not switched on for a completed sprint, which the
     * job would never act on.
     *
     * @param sprint the sprint to validate
     * @throws ValidationException if the sprint is COMPLETED
     */
    public void ensureRecurrenceSprintNotCompleted(Sprint sprint) {
        if (sprint.getStatus() == SprintStatus.COMPLETED) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_RECURRENCE_SPRINT_COMPLETED,
                    sprint.getId());
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_RECURRENCE_SPRINT_COMPLETED,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId()));
        }
    }

    /**
     * Ensures the sprint was not completed between the first read of a settings
     * save and the moment the save holds the settings row.
     *
     * The job completes the sprint and moves its repetition to the next sprint
     * while it holds that row. A save that waited for it would then change only
     * the completed sprint, so the change would never reach the chain and the
     * caller would still be told it was saved.
     *
     * @param sprint          the sprint as read after the row lock
     * @param statusAtRequest the sprint status read before the row lock
     * @throws ConflictException if the sprint is now COMPLETED and was not before
     */
    public void ensureRecurrenceSprintNotCompletedDuringSave(Sprint sprint, SprintStatus statusAtRequest) {
        if (statusAtRequest != SprintStatus.COMPLETED && sprint.getStatus() == SprintStatus.COMPLETED) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_RECURRENCE_SPRINT_COMPLETED_DURING_SAVE,
                    sprint.getId());
            throw new ConflictException(
                    ExceptionDomainKaizenError.STA_SPRINT_RECURRENCE_SPRINT_COMPLETED_DURING_SAVE,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId()));
        }
    }

    /**
     * Ensures a repeating sprint has both dates at 00:00 in
     * SprintRecurrenceConstant.ZONE_ID and a length within the allowed range.
     *
     * The job rolls sprints over at those dates, so a date at another time of
     * day would close the sprint at an unexpected hour, and a missing date would
     * leave nothing to roll over at.
     *
     * @param sprint     the sprint to validate
     * @param lengthDays the sprint length from SprintRecurrenceCalculator, null when a date is missing
     * @throws ValidationException if a date is missing or not at midnight, or the length is out of range
     */
    public void ensureRecurrenceDatesValid(Sprint sprint, Integer lengthDays) {
        boolean valid = isMidnight(sprint.getStartDate())
                && isMidnight(sprint.getEndDate())
                && lengthDays != null
                && lengthDays >= SprintRecurrenceConstant.MIN_LENGTH_DAYS
                && lengthDays <= SprintRecurrenceConstant.MAX_LENGTH_DAYS;
        if (!valid) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_RECURRENCE_DATES_INVALID,
                    sprint.getId(), sprint.getStartDate(), sprint.getEndDate());
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_RECURRENCE_DATES_INVALID,
                    recurrenceErrorValues(sprint, lengthDays));
        }
    }

    /**
     * Ensures the days before the end date are set and leave the next sprint to
     * be created after this sprint starts: from 1 to the length minus 1. Only
     * checked when the next sprint is created before the end date.
     *
     * @param sprint           the sprint to validate
     * @param creationMode     when the next sprint is created
     * @param createDaysBefore days before the end date, nullable
     * @param lengthDays       the sprint length from SprintRecurrenceCalculator
     * @throws ValidationException if the value is missing or out of range for DAYS_BEFORE
     */
    public void ensureRecurrenceDaysBeforeValid(Sprint sprint, SprintRecurrenceCreationMode creationMode,
                                                Integer createDaysBefore, Integer lengthDays) {
        if (creationMode != SprintRecurrenceCreationMode.DAYS_BEFORE) {
            return;
        }
        boolean valid = createDaysBefore != null
                && lengthDays != null
                && createDaysBefore >= SprintRecurrenceConstant.MIN_CREATE_DAYS_BEFORE
                && createDaysBefore < lengthDays;
        if (!valid) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_RECURRENCE_DAYS_BEFORE_INVALID,
                    sprint.getId(), createDaysBefore, lengthDays);
            Map<String, Object> errorValues = recurrenceErrorValues(sprint, lengthDays);
            if (createDaysBefore != null) {
                errorValues.put(SprintRecurrenceConstant.FIELD_CREATE_DAYS_BEFORE, createDaysBefore);
            }
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SPRINT_RECURRENCE_DAYS_BEFORE_INVALID,
                    errorValues);
        }
    }

    /**
     * Ensures a board can join the series: it is in no series, in this series,
     * or in a disabled series, which gives it up. An enabled series holds its
     * boards, because its job creates their next sprints and a second series
     * would create a second sprint for the same board every rollover.
     *
     * @param board    the board to add to the series
     * @param series   the series the board joins, without an id while not yet saved
     * @param existing the board's current series row, if any
     * @throws ConflictException STA_BOARD_IN_OTHER_SPRINT_SERIES when another enabled series holds the board
     */
    public void ensureBoardNotInOtherEnabledSeries(
            Board board,
            SprintSeries series,
            Optional<SprintSeriesBoard> existing) {
        if (existing.isEmpty()) {
            return;
        }
        SprintSeries owner = existing.get().getSeries();
        if (owner.getId().equals(series.getId()) || !owner.isEnabled()) {
            return;
        }
        loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                SprintExceptionLogConstant.SPRINT_SERIES_BOARD_TAKEN, board.getId(), series.getId(), owner.getId());
        Map<String, Object> errorValues = new HashMap<>();
        errorValues.put(SprintConstant.FIELD_BOARD_ID, board.getId());
        errorValues.put(SprintConstant.FIELD_BOARD_NAME, board.getName());
        errorValues.put(SprintRecurrenceConstant.FIELD_SERIES_ID, owner.getId());
        errorValues.put(SprintRecurrenceConstant.FIELD_SERIES_NAME, owner.getName());
        throw new ConflictException(ExceptionDomainKaizenError.STA_BOARD_IN_OTHER_SPRINT_SERIES, errorValues);
    }

    /**
     * Ensures a series name is 1 to 240 characters once trimmed, so the names
     * "{series name} {round}" of its sprints fit the sprint name column.
     *
     * @param seriesName the series name as sent or taken from a sprint name, nullable
     * @param sprintId   the sprint the name came from, null for a sprint not yet saved
     * @throws ValidationException VAL_SPRINT_SERIES_NAME_INVALID if the name is blank or too long
     */
    public void ensureSeriesNameValid(String seriesName, UUID sprintId) {
        String trimmed = seriesName == null ? "" : seriesName.trim();
        if (trimmed.isEmpty() || trimmed.length() > SprintRecurrenceConstant.SERIES_NAME_MAX_LENGTH) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_SERIES_NAME_INVALID, sprintId, trimmed.length());
            Map<String, Object> errorValues = new HashMap<>();
            errorValues.put(SprintRecurrenceConstant.FIELD_SERIES_NAME, trimmed);
            if (sprintId != null) {
                errorValues.put(SprintConstant.FIELD_SPRINT_ID, sprintId);
            }
            throw new ValidationException(ExceptionDomainKaizenError.VAL_SPRINT_SERIES_NAME_INVALID, errorValues);
        }
    }

    /**
     * Ensures an update names the sprint the way its series allows. A sprint of
     * an enabled series is named after the series, so it takes no name of its
     * own and is renamed only through seriesName, and only while it is not
     * COMPLETED, because a completed sprint keeps the name it ran under. Any
     * other sprint takes a name and no seriesName.
     *
     * @param sprint  the sprint being updated
     * @param request the update request
     * @throws ValidationException VAL_SPRINT_SERIES_NAME_INVALID when the request breaks the rule
     */
    public void ensureSeriesNaming(Sprint sprint, SprintUpdateRequest request) {
        boolean inEnabledSeries = sprint.getSeries() != null && sprint.getSeries().isEnabled();
        boolean refused = inEnabledSeries
                ? request.name() != null
                        || (request.seriesName() != null && sprint.getStatus() == SprintStatus.COMPLETED)
                : request.seriesName() != null;
        if (refused) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    SprintExceptionLogConstant.SPRINT_SERIES_NAMING_REFUSED,
                    sprint.getId(), request.name() != null, request.seriesName() != null);
            throw new ValidationException(ExceptionDomainKaizenError.VAL_SPRINT_SERIES_NAME_INVALID,
                    Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId()));
        }
        if (request.seriesName() != null) {
            ensureSeriesNameValid(request.seriesName(), sprint.getId());
        }
    }

    /**
     * Ensures a complete request that moves the unfinished tickets to the next
     * sprint of the series can be honoured. That next sprint is created or
     * started by the completion itself, so the request may name no target sprint
     * or board, its action must be MOVE_TO_SPRINT, and the sprint must belong to
     * an enabled series that still has a live board to link the next sprint to.
     * A request without moveToNextSprint passes.
     *
     * @param sprint             the sprint being completed
     * @param request            the complete request
     * @param seriesHasLiveBoard whether the sprint's series has a board that is not deleted
     * @throws ValidationException {@link ExceptionDomainKaizenError#VAL_SPRINT_NEXT_SPRINT_UNAVAILABLE} when the request breaks the rule
     */
    public void ensureMoveToNextSprintAllowed(Sprint sprint, SprintCompleteRequest request,
                                              boolean seriesHasLiveBoard) {
        if (!Boolean.TRUE.equals(request.moveToNextSprint())) {
            return;
        }
        boolean enabledSeries = sprint.getSeries() != null && sprint.getSeries().isEnabled();
        boolean allowed = request.ticketAction() == TicketRelocationAction.MOVE_TO_SPRINT
                && request.targetSprintId() == null
                && request.targetBoardId() == null
                && enabledSeries
                && seriesHasLiveBoard;
        if (allowed) {
            return;
        }
        loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                SprintExceptionLogConstant.SPRINT_NEXT_SPRINT_UNAVAILABLE, sprint.getId(), request.ticketAction(),
                request.targetSprintId(), request.targetBoardId(), enabledSeries, seriesHasLiveBoard);
        throw new ValidationException(ExceptionDomainKaizenError.VAL_SPRINT_NEXT_SPRINT_UNAVAILABLE,
                Map.of(SprintConstant.FIELD_SPRINT_ID, sprint.getId()));
    }

    /**
     * True when the instant is 00:00 in SprintRecurrenceConstant.ZONE_ID; false for null.
     */
    private static boolean isMidnight(Instant instant) {
        return instant != null
                && instant.atZone(SprintRecurrenceConstant.ZONE_ID).toLocalTime().equals(LocalTime.MIDNIGHT);
    }

    /**
     * Error values of a recurrence rule; a missing date or length is left out
     * rather than sent as null.
     */
    private static Map<String, Object> recurrenceErrorValues(Sprint sprint, Integer lengthDays) {
        Map<String, Object> errorValues = new HashMap<>();
        errorValues.put(SprintConstant.FIELD_SPRINT_ID, sprint.getId());
        if (sprint.getStartDate() != null) {
            errorValues.put(SprintConstant.FIELD_START_DATE, sprint.getStartDate());
        }
        if (sprint.getEndDate() != null) {
            errorValues.put(SprintConstant.FIELD_END_DATE, sprint.getEndDate());
        }
        if (lengthDays != null) {
            errorValues.put(SprintRecurrenceConstant.FIELD_LENGTH_DAYS, lengthDays);
        }
        return errorValues;
    }
}
