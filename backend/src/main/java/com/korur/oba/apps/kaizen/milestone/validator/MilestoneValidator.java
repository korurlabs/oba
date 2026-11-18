package com.korur.oba.apps.kaizen.milestone.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.milestone.constant.MilestoneConstant;
import com.korur.oba.apps.kaizen.milestone.constant.MilestoneExceptionLogConstant;
import com.korur.oba.apps.kaizen.milestone.entity.Milestone;
import com.korur.oba.apps.kaizen.milestone.entity.MilestoneDependency;
import com.korur.oba.apps.kaizen.milestone.enums.MilestoneStatus;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneDependencyRepository;
import com.korur.oba.apps.kaizen.milestone.repository.MilestoneRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for milestone lookups, dates, status transitions, the board
 * relation between a milestone and a ticket, and milestone dependencies.
 *
 * Depends only on its own domain's repositories so that board access and
 * ticket helpers can use it without a bean cycle.
 */
@Component
@RequiredArgsConstructor
public class MilestoneValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(MilestoneValidator.class);

    private final MilestoneRepository milestoneRepository;
    private final MilestoneDependencyRepository milestoneDependencyRepository;
    private final LoggingService loggingService;

    /**
     * Ensures a live milestone exists on the board and returns it.
     *
     * @param boardId     ID of the board from the URL path
     * @param milestoneId ID of the milestone
     * @return the milestone
     * @throws NotFoundException when the milestone does not live on the board
     */
    public Milestone ensureMilestoneExists(UUID boardId, UUID milestoneId) {
        return milestoneRepository.findByBoard_IdAndId(boardId, milestoneId)
                .orElseThrow(() -> notFound(milestoneId));
    }

    /**
     * Ensures a milestone exists on the board, soft-deleted ones included, and returns it.
     * Used by restore, which must reach deleted milestones.
     *
     * @param boardId     ID of the board from the URL path
     * @param milestoneId ID of the milestone
     * @return the milestone
     * @throws NotFoundException when the milestone does not exist on the board
     */
    public Milestone ensureMilestoneExistsIncludingDeleted(UUID boardId, UUID milestoneId) {
        return milestoneRepository.findByIdWithDeleted(milestoneId)
                .filter(milestone -> milestone.getBoard().getId().equals(boardId))
                .orElseThrow(() -> notFound(milestoneId));
    }

    /**
     * Ensures a live milestone exists on one of the given boards and returns it.
     * Callers pass the boards the user is a member of, so a milestone on any
     * other board is reported as not found rather than revealed.
     *
     * @param milestoneId ID of the milestone
     * @param boardIds    IDs of the boards the milestone may live on
     * @return the milestone
     * @throws NotFoundException when the milestone is missing or on another board
     */
    public Milestone ensureMilestoneExistsOnBoards(UUID milestoneId, Collection<UUID> boardIds) {
        return milestoneRepository.findById(milestoneId)
                .filter(milestone -> boardIds.contains(milestone.getBoard().getId()))
                .orElseThrow(() -> notFound(milestoneId));
    }

    /**
     * Ensures the start date, when present, is on or before the target date.
     *
     * @param startDate  start date, nullable
     * @param targetDate target date
     * @throws ValidationException when the start date is after the target date
     */
    public void ensureStartNotAfterTarget(LocalDate startDate, LocalDate targetDate) {
        if (startDate != null && targetDate != null && startDate.isAfter(targetDate)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.MILESTONE_START_AFTER_TARGET, startDate, targetDate);
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_MILESTONE_START_AFTER_TARGET,
                    Map.of(MilestoneConstant.FIELD_START_DATE, startDate,
                            MilestoneConstant.FIELD_TARGET_DATE, targetDate));
        }
    }

    /**
     * Ensures the milestone is OPEN, so that it can be closed.
     *
     * @param milestone the milestone
     * @throws ConflictException when the milestone is already closed
     */
    public void ensureMilestoneIsOpen(Milestone milestone) {
        if (milestone.getStatus() != MilestoneStatus.OPEN) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.MILESTONE_STATUS_UNCHANGED, milestone.getId(), milestone.getStatus());
            throw new ConflictException(
                    ExceptionDomainKaizenError.STA_MILESTONE_ALREADY_CLOSED,
                    Map.of(MilestoneConstant.FIELD_MILESTONE_ID, milestone.getId()));
        }
    }

    /**
     * Ensures the milestone is CLOSED, so that it can be reopened.
     *
     * @param milestone the milestone
     * @throws ConflictException when the milestone is already open
     */
    public void ensureMilestoneIsClosed(Milestone milestone) {
        if (milestone.getStatus() != MilestoneStatus.CLOSED) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.MILESTONE_STATUS_UNCHANGED, milestone.getId(), milestone.getStatus());
            throw new ConflictException(
                    ExceptionDomainKaizenError.STA_MILESTONE_ALREADY_OPEN,
                    Map.of(MilestoneConstant.FIELD_MILESTONE_ID, milestone.getId()));
        }
    }

    /**
     * Ensures the milestone is soft-deleted, so that it can be restored.
     *
     * @param milestone the milestone
     * @throws ValidationException when the milestone is not deleted
     */
    public void ensureMilestoneIsDeleted(Milestone milestone) {
        if (!milestone.isDeleted()) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.MILESTONE_NOT_DELETED, milestone.getId());
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_MILESTONE_NOT_DELETED,
                    Map.of(MilestoneConstant.FIELD_MILESTONE_ID, milestone.getId()));
        }
    }

    /**
     * Tells whether a ticket on ticketBoard may count towards a milestone on
     * milestoneBoard: the same board, a parent, a child, or a sibling (both
     * boards share a parent). Uses parent ids only, so it needs no queries.
     *
     * @param milestoneBoard board of the milestone
     * @param ticketBoard    board of the ticket
     * @return true when the boards are related
     */
    public boolean isBoardRelated(Board milestoneBoard, Board ticketBoard) {
        UUID milestoneBoardId = milestoneBoard.getId();
        UUID ticketBoardId = ticketBoard.getId();
        if (milestoneBoardId.equals(ticketBoardId)) {
            return true;
        }

        UUID milestoneParentId = milestoneBoard.getParentBoard() != null
                ? milestoneBoard.getParentBoard().getId()
                : null;
        UUID ticketParentId = ticketBoard.getParentBoard() != null
                ? ticketBoard.getParentBoard().getId()
                : null;

        // The ticket's board is a child of the milestone's board.
        if (milestoneBoardId.equals(ticketParentId)) {
            return true;
        }
        // The ticket's board is the parent of the milestone's board.
        if (ticketBoardId.equals(milestoneParentId)) {
            return true;
        }
        // Siblings: both boards share a parent.
        return milestoneParentId != null && milestoneParentId.equals(ticketParentId);
    }

    /**
     * Ensures a ticket on the board may be attached to the milestone.
     *
     * @param milestone   the milestone
     * @param ticketBoard board of the ticket
     * @throws ValidationException when the boards are not related
     */
    public void ensureMilestoneLinkableToBoard(Milestone milestone, Board ticketBoard) {
        if (!isBoardRelated(milestone.getBoard(), ticketBoard)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.MILESTONE_NOT_LINKABLE, milestone.getId(), ticketBoard.getId());
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_MILESTONE_NOT_LINKABLE,
                    Map.of(MilestoneConstant.FIELD_MILESTONE_ID, milestone.getId(),
                            MilestoneConstant.FIELD_BOARD_ID, ticketBoard.getId()));
        }
    }

    /**
     * Ensures that milestone may depend on dependsOn: not on itself, only on a
     * milestone of a related board (the rule of ticket attachment), and never
     * so that the dependencies form a cycle.
     *
     * @param milestone the dependent milestone
     * @param dependsOn the milestone it would depend on
     * @throws ValidationException when the dependency is not allowed
     */
    public void ensureDependencyAllowed(Milestone milestone, Milestone dependsOn) {
        Map<String, Object> values = Map.of(MilestoneConstant.FIELD_MILESTONE_ID, milestone.getId(),
                MilestoneConstant.FIELD_DEPENDS_ON_MILESTONE_ID, dependsOn.getId());

        if (milestone.getId().equals(dependsOn.getId())) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.MILESTONE_DEPENDENCY_SELF, milestone.getId());
            throw new ValidationException(ExceptionDomainKaizenError.VAL_MILESTONE_DEPENDENCY_SELF, values);
        }
        if (!isBoardRelated(milestone.getBoard(), dependsOn.getBoard())) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.MILESTONE_DEPENDENCY_NOT_RELATED, milestone.getId(), dependsOn.getId());
            throw new ValidationException(ExceptionDomainKaizenError.VAL_MILESTONE_DEPENDENCY_NOT_RELATED, values);
        }
        // The new edge milestone -> dependsOn closes a cycle exactly when dependsOn
        // already reaches milestone through existing edges.
        if (dependsOnReaches(dependsOn.getId(), milestone.getId())) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.MILESTONE_DEPENDENCY_CYCLE, milestone.getId(), dependsOn.getId());
            throw new ValidationException(ExceptionDomainKaizenError.VAL_MILESTONE_DEPENDENCY_CYCLE, values);
        }
    }

    /**
     * Ensures the dependency row exists and returns it.
     *
     * @param milestoneId          ID of the dependent milestone
     * @param dependsOnMilestoneId ID of the milestone it depends on
     * @return the dependency row
     * @throws NotFoundException when the milestone does not depend on the other
     */
    public MilestoneDependency ensureDependencyExists(UUID milestoneId, UUID dependsOnMilestoneId) {
        return milestoneDependencyRepository.findByMilestoneIdAndDependsOnMilestoneId(milestoneId, dependsOnMilestoneId)
                .orElseThrow(() -> {
                    loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                            MilestoneExceptionLogConstant.MILESTONE_DEPENDENCY_NOT_FOUND,
                            milestoneId, dependsOnMilestoneId);
                    return new NotFoundException(
                            ExceptionDomainKaizenError.NOT_MILESTONE_DEPENDENCY_FOUND,
                            Map.of(MilestoneConstant.FIELD_MILESTONE_ID, milestoneId,
                                    MilestoneConstant.FIELD_DEPENDS_ON_MILESTONE_ID, dependsOnMilestoneId));
                });
    }

    /**
     * Walks the dependency graph breadth first from startId and tells whether
     * targetId is reachable. Rows of soft-deleted milestones count, so that a
     * restore can never complete a cycle. One query per depth level; the
     * visited set stops the walk on any cycle already in the data.
     */
    private boolean dependsOnReaches(UUID startId, UUID targetId) {
        Set<UUID> visited = new HashSet<>();
        visited.add(startId);
        List<UUID> frontier = List.of(startId);
        while (!frontier.isEmpty()) {
            List<UUID> next = new ArrayList<>();
            for (UUID id : milestoneDependencyRepository.findDependsOnIdsByMilestoneIds(frontier)) {
                if (id.equals(targetId)) {
                    return true;
                }
                if (visited.add(id)) {
                    next.add(id);
                }
            }
            frontier = next;
        }
        return false;
    }

    /**
     * Ensures the roadmap window is not inverted.
     *
     * @param from window start, nullable
     * @param to   window end, nullable
     * @throws ValidationException when from is after to
     */
    public void ensureWindowValid(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    MilestoneExceptionLogConstant.ROADMAP_FROM_AFTER_TO, from, to);
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_ROADMAP_FROM_AFTER_TO,
                    Map.of(MilestoneConstant.FIELD_FROM, from,
                            MilestoneConstant.FIELD_TO, to));
        }
    }

    /**
     * Logs and builds the not-found error of a milestone.
     */
    private NotFoundException notFound(UUID milestoneId) {
        loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                MilestoneExceptionLogConstant.MILESTONE_NOT_FOUND, milestoneId);
        return new NotFoundException(
                ExceptionDomainKaizenError.NOT_MILESTONE_FOUND,
                Map.of(MilestoneConstant.FIELD_MILESTONE_ID, milestoneId));
    }
}
