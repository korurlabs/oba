package com.korur.oba.apps.kaizen.ticket.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.entity.Backlog;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.repository.BoardRepository;
import com.korur.oba.apps.kaizen.comment.repository.CommentRepository;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.milestone.validator.MilestoneValidator;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.ticket.constant.TicketConstant;
import com.korur.oba.apps.kaizen.ticket.constant.service.TicketServiceLogConstant;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.enums.SubTicketMoveMode;
import com.korur.oba.apps.kaizen.ticket.enums.TicketRelocationAction;
import com.korur.oba.apps.kaizen.ticket.enums.TicketStatus;
import com.korur.oba.apps.kaizen.ticket.repository.TicketAssigneeRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketLabelRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.apps.kaizen.ticket.repository.TicketReviewerRepository;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Helper for ticket data operations used during board and sprint lifecycle events.
 *
 * Centralizes ticket handling logic (move, delete, restore children) that is
 * shared across board and sprint deletion/restoration workflows.
 *
 * It also owns the placement rules of the ticket hierarchy. A sub-ticket is
 * planned on its own: its own sprint or backlog is its placement, and no move
 * changes its parent. A user move takes the sub-tickets the user picks along
 * (see {@link SubTicketMoveMode}); a system relocation moves exactly the tickets
 * it selected. A sub-ticket never changes board without its parent.
 */
@Component
@RequiredArgsConstructor
public class TicketHelper {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(TicketHelper.class);

    private final TicketRepository ticketRepository;
    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final TicketAssigneeRepository ticketAssigneeRepository;
    private final TicketReviewerRepository ticketReviewerRepository;
    private final TicketLabelRepository ticketLabelRepository;
    private final TicketStatusNormalizer ticketStatusNormalizer;
    private final MilestoneValidator milestoneValidator;
    private final LoggingService loggingService;

    /**
     * Moves tickets on behalf of a user (move, bulk move, add to backlog) together
     * with the sub-tickets the mode picks, then relocates them.
     *
     * The mode decides which descendants of the requested tickets come along:
     * {@link SubTicketMoveMode#NONE} none, {@link SubTicketMoveMode#UNFINISHED} those
     * not {@link TicketStatus#DONE} that are placed where their nearest requested
     * ancestor was before the move, {@link SubTicketMoveMode#SELECTED} exactly
     * subTicketIds. A null mode is {@link SubTicketMoveMode#UNFINISHED}, and
     * {@link SubTicketMoveMode#SELECTED} without ids moves no descendant. A requested ticket
     * that changes board carries its whole subtree whatever the mode, and a
     * requested sub-ticket may only change board together with an ancestor.
     *
     * Every check runs before any ticket is written.
     *
     * @param tickets         the tickets the user asked to move
     * @param mode            which sub-tickets come along; null means {@link SubTicketMoveMode#UNFINISHED}
     * @param subTicketIds    the descendants to move along with {@link SubTicketMoveMode#SELECTED};
     *                        null or empty otherwise
     * @param action          {@link TicketRelocationAction#MOVE_TO_BACKLOG} or
     *                        {@link TicketRelocationAction#MOVE_TO_SPRINT}
     * @param targetBoard     the target board (null = each ticket's own board)
     * @param targetSprint    the target sprint for {@link TicketRelocationAction#MOVE_TO_SPRINT}
     * @param targetBacklog   the target backlog for {@link TicketRelocationAction#MOVE_TO_BACKLOG}
     * @param sourceContextId the source context ID (board) for logging
     * @return keys (after the move) of tickets whose milestone was cleared because
     *         the milestone's board does not relate to the target board
     * @throws ValidationException KAI_VAL_057 when subTicketIds come without
     *         {@link SubTicketMoveMode#SELECTED}, KAI_VAL_058 when one of them is neither a
     *         requested ticket nor a descendant of one,
     *         KAI_VAL_059 when a sub-ticket would change board without its parent
     */
    public List<String> moveTickets(
            List<Ticket> tickets,
            SubTicketMoveMode mode,
            List<UUID> subTicketIds,
            TicketRelocationAction action,
            Board targetBoard,
            Sprint targetSprint,
            Backlog targetBacklog,
            UUID sourceContextId) {
        SubTicketMoveMode effectiveMode = mode != null ? mode : SubTicketMoveMode.UNFINISHED;
        List<UUID> selectedIds = subTicketIds != null ? subTicketIds : List.of();
        if (!selectedIds.isEmpty() && effectiveMode != SubTicketMoveMode.SELECTED) {
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SUB_TICKET_IDS_WITHOUT_SELECTED,
                    Map.of(TicketConstant.FIELD_SUB_TICKET_MODE, effectiveMode));
        }

        Set<UUID> requestedIds = idsOf(tickets);
        List<Ticket> descendants = collectWithSubTickets(tickets);
        descendants.removeIf((Ticket candidate) -> requestedIds.contains(candidate.getId()));

        Set<UUID> movableIds = new HashSet<>(requestedIds);
        movableIds.addAll(idsOf(descendants));
        ensureSelectedAreMovable(selectedIds, movableIds);
        ensureNoSubTicketLeavesItsParentsBoard(tickets, requestedIds, targetBoard);

        Map<UUID, Ticket> ticketsToMove = new LinkedHashMap<>();
        for (Ticket ticket : tickets) {
            ticketsToMove.put(ticket.getId(), ticket);
        }
        for (Ticket descendant : descendants) {
            Ticket movedAncestor = findNearestAncestorIn(descendant, requestedIds);
            // A ticket that changes board brings its whole subtree in relocateTickets.
            if (movedAncestor == null || changesBoard(movedAncestor, targetBoard)) {
                continue;
            }
            if (isPickedByMode(effectiveMode, descendant, movedAncestor, selectedIds)) {
                ticketsToMove.putIfAbsent(descendant.getId(), descendant);
            }
        }

        return relocateTickets(new ArrayList<>(ticketsToMove.values()), action,
                targetBoard, targetSprint, targetBacklog, sourceContextId);
    }

    /**
     * Relocates exactly the given tickets according to the specified action. System
     * relocations (sprint complete, delete and unlink, recurring rollover, board
     * delete) call it directly, and {@link #moveTickets} ends in it.
     *
     * Supports same-board and cross-board moves. When the target board differs
     * from the ticket's current board, cross-board fields (board, key, displayOrder,
     * project, milestone) are updated automatically.
     *
     * No parent changes. A ticket that changes board carries its whole subtree, so
     * a sub-ticket never sits on another board than its parent. A given sub-ticket
     * that would change board while its parent stays goes to the backlog of its own
     * board instead, with its given descendants. {@link TicketRelocationAction#DELETE}
     * cascades to sub-tickets via JPA {@link jakarta.persistence.CascadeType#REMOVE}.
     *
     * @param tickets         the tickets to relocate
     * @param action          the relocation action to perform
     * @param targetBoard     the target board (null = same board)
     * @param targetSprint    the target sprint for {@link TicketRelocationAction#MOVE_TO_SPRINT}
     * @param targetBacklog   the target backlog for {@link TicketRelocationAction#MOVE_TO_BACKLOG}
     * @param sourceContextId the source context ID (sprint/board) for logging
     * @return keys (after the move) of tickets whose milestone was cleared because
     *         the milestone's board does not relate to the target board
     */
    public List<String> relocateTickets(
            List<Ticket> tickets,
            TicketRelocationAction action,
            Board targetBoard,
            Sprint targetSprint,
            Backlog targetBacklog,
            UUID sourceContextId) {
        if (tickets.isEmpty()) {
            return List.of();
        }

        if (action == TicketRelocationAction.DELETE) {
            ticketRepository.deleteAll(tickets);
            loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                    TicketServiceLogConstant.TICKETS_RELOCATED,
                    tickets.size(), "DELETE", sourceContextId);
            return List.of();
        }

        Set<UUID> givenIds = idsOf(tickets);
        List<Ticket> keptOnOwnBoard = new ArrayList<>();
        List<Ticket> movingTickets = new ArrayList<>();
        for (Ticket ticket : tickets) {
            Ticket topGivenTicket = findTopAncestorIn(ticket, givenIds);
            if (topGivenTicket.getParentTicket() != null && changesBoard(topGivenTicket, targetBoard)) {
                keptOnOwnBoard.add(ticket);
            } else {
                movingTickets.add(ticket);
            }
        }
        movingTickets = withSubtreesOfBoardChanges(movingTickets, targetBoard);

        List<String> milestoneClearedTicketKeys = new ArrayList<>();
        switch (action) {
            case MOVE_TO_BACKLOG -> {
                for (Ticket ticket : movingTickets) {
                    ticket.setSprint(null);
                    ticket.setBacklog(targetBacklog);
                    if (changesBoard(ticket, targetBoard)) {
                        applyCrossBoardFields(ticket, targetBoard, milestoneClearedTicketKeys);
                    }
                }
                // Backlog tickets may only be TODO.
                ticketStatusNormalizer.normalizeBacklogStatus(movingTickets);
                ticketRepository.saveAll(movingTickets);
                loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                        TicketServiceLogConstant.TICKETS_RELOCATED,
                        movingTickets.size(), "MOVE_TO_BACKLOG", sourceContextId);
            }
            case MOVE_TO_SPRINT -> {
                for (Ticket ticket : movingTickets) {
                    ticket.setBacklog(null);
                    ticket.setSprint(targetSprint);
                    if (changesBoard(ticket, targetBoard)) {
                        applyCrossBoardFields(ticket, targetBoard, milestoneClearedTicketKeys);
                    }
                }
                ticketRepository.saveAll(movingTickets);
                loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                        TicketServiceLogConstant.TICKETS_RELOCATED,
                        movingTickets.size(), "MOVE_TO_SPRINT", sourceContextId);
            }
            case DELETE -> { /* handled before the split */ }
        }

        moveToOwnBoardBacklog(keptOnOwnBoard, targetBoard);
        return milestoneClearedTicketKeys;
    }

    /**
     * Detaches the direct sub-tickets of every given ticket that changes board, so they
     * stay on their own board as top-level tickets while their parent moves. Each keeps
     * its own placement and its own sub-tickets, as on an unparent, and a backlog one
     * becomes TODO. A sub-ticket that is itself given keeps its parent and moves with it.
     * The detached tickets are flushed, so the subtree lookup of the move that follows
     * no longer finds them.
     *
     * @param tickets     the tickets the move was asked for
     * @param targetBoard the board they move to
     */
    public void detachSubTicketsFromBoardChanges(List<Ticket> tickets, Board targetBoard) {
        Set<UUID> givenIds = idsOf(tickets);
        List<Ticket> detached = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (!changesBoard(ticket, targetBoard)) {
                continue;
            }
            List<Ticket> leaving = new ArrayList<>();
            for (Ticket subTicket : ticketRepository.findByParentTicket_IdIn(List.of(ticket.getId()))) {
                if (!givenIds.contains(subTicket.getId())) {
                    leaving.add(subTicket);
                }
            }
            for (Ticket subTicket : leaving) {
                unparent(subTicket);
                loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                        TicketServiceLogConstant.TICKET_DETACHED_ON_BOARD_CHANGE,
                        subTicket.getKey(), ticket.getKey(), targetBoard.getId());
            }
            ticket.getSubTickets().removeAll(leaving);
            detached.addAll(leaving);
        }
        if (detached.isEmpty()) {
            return;
        }
        ticketStatusNormalizer.normalizeBacklogStatus(detached);
        ticketRepository.saveAllAndFlush(detached);
    }

    /**
     * Makes ticket a top-level ticket. It keeps its own placement and so do its
     * sub-tickets. The caller applies the backlog status rule and saves the ticket.
     *
     * @param ticket the sub-ticket to unparent; a ticket without parent is left unchanged
     */
    public void unparent(Ticket ticket) {
        ticket.setParentTicket(null);
    }

    /**
     * Makes ticket a sub-ticket of parentTicket. It keeps its own placement, since a
     * sub-ticket is planned independently of its parent. The caller saves the ticket.
     *
     * @param ticket       the ticket that gets a new parent
     * @param parentTicket the new parent
     */
    public void assignParent(Ticket ticket, Ticket parentTicket) {
        ticket.setParentTicket(parentTicket);
    }

    /**
     * Restores all cascade-deleted children of a ticket (comments, assignees,
     * reviewers, ticketLabels, and sub-tickets recursively).
     */
    public void restoreTicketChildren(UUID ticketId) {
        commentRepository.restoreAllByTicketId(ticketId);
        ticketAssigneeRepository.restoreAllByTicketId(ticketId);
        ticketReviewerRepository.restoreAllByTicketId(ticketId);
        ticketLabelRepository.restoreAllByTicketId(ticketId);

        List<Ticket> deletedSubTickets = ticketRepository.findDeletedByParentTicketId(ticketId);
        for (Ticket subTicket : deletedSubTickets) {
            ticketRepository.restoreById(subTicket.getId());
            restoreTicketChildren(subTicket.getId());
        }
    }

    /**
     * Refuses subTicketIds that are neither a requested ticket nor a descendant of
     * one, so a request can never pull in a ticket the board access checks did not
     * cover. A listed id that is itself requested moves anyway and is accepted.
     */
    private void ensureSelectedAreMovable(List<UUID> selectedIds, Set<UUID> movableIds) {
        List<UUID> foreignIds = selectedIds.stream()
                .filter((UUID selectedId) -> !movableIds.contains(selectedId))
                .distinct()
                .toList();
        if (!foreignIds.isEmpty()) {
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SUB_TICKET_NOT_DESCENDANT,
                    Map.of(TicketConstant.FIELD_SUB_TICKET_IDS, foreignIds));
        }
    }

    /**
     * Refuses a user move in which a requested sub-ticket would change board while
     * none of its ancestors is moved, since a sub-ticket and its parent always share
     * a board. The user detaches the sub-ticket first.
     */
    private void ensureNoSubTicketLeavesItsParentsBoard(
            List<Ticket> tickets, Set<UUID> requestedIds, Board targetBoard) {
        List<String> strandedKeys = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (ticket.getParentTicket() != null
                    && changesBoard(ticket, targetBoard)
                    && findNearestAncestorIn(ticket, requestedIds) == null) {
                strandedKeys.add(ticket.getKey());
            }
        }
        if (!strandedKeys.isEmpty()) {
            throw new ValidationException(
                    ExceptionDomainKaizenError.VAL_SUB_TICKET_BOARD_CHANGE,
                    Map.of(TicketConstant.FIELD_TICKET_KEYS, strandedKeys));
        }
    }

    /**
     * Tells whether the mode moves descendant along with movedAncestor, its nearest
     * requested ancestor. Placements are read before anything moves.
     */
    private boolean isPickedByMode(
            SubTicketMoveMode mode, Ticket descendant, Ticket movedAncestor, List<UUID> selectedIds) {
        return switch (mode) {
            case NONE -> false;
            case UNFINISHED -> descendant.getStatus() != TicketStatus.DONE
                    && hasSamePlacement(descendant, movedAncestor);
            case SELECTED -> selectedIds.contains(descendant.getId());
        };
    }

    /**
     * Tells whether two tickets are listed in the same place. A ticket's sprint wins
     * over its backlog, as in the sprint and backlog lists.
     */
    private boolean hasSamePlacement(Ticket ticket, Ticket other) {
        if (ticket.getSprint() != null || other.getSprint() != null) {
            return ticket.getSprint() != null
                    && other.getSprint() != null
                    && ticket.getSprint().getId().equals(other.getSprint().getId());
        }
        return ticket.getBacklog() != null
                && other.getBacklog() != null
                && ticket.getBacklog().getId().equals(other.getBacklog().getId());
    }

    /**
     * Tells whether a move to targetBoard takes ticket off its board; a null target
     * board keeps every ticket on its own board.
     */
    private boolean changesBoard(Ticket ticket, Board targetBoard) {
        return targetBoard != null && !targetBoard.getId().equals(ticket.getBoard().getId());
    }

    /**
     * Returns the nearest ancestor of ticket whose id is in ids, or null when none
     * is. The visited ids end the walk on a parent cycle.
     */
    private Ticket findNearestAncestorIn(Ticket ticket, Set<UUID> ids) {
        Set<UUID> visitedIds = new HashSet<>();
        visitedIds.add(ticket.getId());
        Ticket ancestor = ticket.getParentTicket();
        while (ancestor != null && visitedIds.add(ancestor.getId())) {
            if (ids.contains(ancestor.getId())) {
                return ancestor;
            }
            ancestor = ancestor.getParentTicket();
        }
        return null;
    }

    /**
     * Returns the highest ancestor of ticket whose id is in ids, or the ticket itself
     * when none is. The visited ids end the walk on a parent cycle.
     */
    private Ticket findTopAncestorIn(Ticket ticket, Set<UUID> ids) {
        Set<UUID> visitedIds = new HashSet<>();
        visitedIds.add(ticket.getId());
        Ticket top = ticket;
        Ticket ancestor = ticket.getParentTicket();
        while (ancestor != null && visitedIds.add(ancestor.getId())) {
            if (ids.contains(ancestor.getId())) {
                top = ancestor;
            }
            ancestor = ancestor.getParentTicket();
        }
        return top;
    }

    /**
     * Adds the whole subtree of every ticket that changes board, so its sub-tickets
     * follow it to the target board. Tickets that stay on their board are returned
     * as given, without their sub-tickets.
     */
    private List<Ticket> withSubtreesOfBoardChanges(List<Ticket> tickets, Board targetBoard) {
        List<Ticket> boardChangingTickets = tickets.stream()
                .filter((Ticket ticket) -> changesBoard(ticket, targetBoard))
                .toList();
        if (boardChangingTickets.isEmpty()) {
            return tickets;
        }

        Map<UUID, Ticket> allTickets = new LinkedHashMap<>();
        for (Ticket ticket : tickets) {
            allTickets.put(ticket.getId(), ticket);
        }
        for (Ticket ticket : collectWithSubTickets(boardChangingTickets)) {
            allTickets.putIfAbsent(ticket.getId(), ticket);
        }
        return new ArrayList<>(allTickets.values());
    }

    /**
     * Moves sub-tickets that a cross-board relocation selected without their parent
     * to the backlog of their own board, so they stay on their parent's board and
     * keep their parent. Sub-tickets keep their status in the backlog.
     */
    private void moveToOwnBoardBacklog(List<Ticket> tickets, Board targetBoard) {
        if (tickets.isEmpty()) {
            return;
        }
        for (Ticket ticket : tickets) {
            ticket.setSprint(null);
            ticket.setBacklog(ticket.getBoard().getBacklog());
            loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                    TicketServiceLogConstant.TICKET_KEPT_ON_BOARD_ON_RELOCATION,
                    ticket.getKey(), targetBoard.getId(), ticket.getParentTicket().getKey());
        }
        ticketStatusNormalizer.normalizeBacklogStatus(tickets);
        ticketRepository.saveAll(tickets);
    }

    private Set<UUID> idsOf(List<Ticket> tickets) {
        Set<UUID> ids = new HashSet<>();
        for (Ticket ticket : tickets) {
            ids.add(ticket.getId());
        }
        return ids;
    }

    /**
     * Collects the given tickets together with all their sub-tickets (breadth-first).
     * Handles arbitrary nesting depth without recursion.
     */
    private List<Ticket> collectWithSubTickets(List<Ticket> tickets) {
        Map<UUID, Ticket> allTickets = new LinkedHashMap<>();
        for (Ticket t : tickets) {
            allTickets.put(t.getId(), t);
        }

        List<UUID> parentIds = new ArrayList<>(allTickets.keySet());
        while (!parentIds.isEmpty()) {
            List<Ticket> subTickets = ticketRepository.findByParentTicket_IdIn(parentIds);
            parentIds = new ArrayList<>();
            for (Ticket sub : subTickets) {
                if (allTickets.putIfAbsent(sub.getId(), sub) == null) {
                    parentIds.add(sub.getId());
                }
            }
        }

        return new ArrayList<>(allTickets.values());
    }

    /**
     * Applies cross-board fields when a ticket is moved to a different board.
     * Clears the project, regenerates the ticket key with the target board's prefix,
     * and assigns the next display order on the target board.
     *
     * The milestone is kept when its board still relates to the target board and
     * cleared otherwise, since a milestone only tracks tickets of its board family;
     * the new key of a cleared ticket is added to milestoneClearedTicketKeys so
     * the caller can tell the user.
     */
    private void applyCrossBoardFields(Ticket ticket, Board targetBoard, List<String> milestoneClearedTicketKeys) {
        ticket.setBoard(targetBoard);
        ticket.setProject(null);
        long nextNumber = boardRepository.incrementAndGetTicketCounter(targetBoard.getId());
        ticket.setKey(targetBoard.getPrefix() + "-" + nextNumber);
        int nextOrder = ticketRepository.maxDisplayOrderByBoard_Id(targetBoard.getId()) + 1;
        ticket.setDisplayOrder(nextOrder);
        if (ticket.getMilestone() != null
                && !milestoneValidator.isBoardRelated(ticket.getMilestone().getBoard(), targetBoard)) {
            ticket.setMilestone(null);
            milestoneClearedTicketKeys.add(ticket.getKey());
        }
    }
}
