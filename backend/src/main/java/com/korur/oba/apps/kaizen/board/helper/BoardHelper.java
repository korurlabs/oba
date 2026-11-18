package com.korur.oba.apps.kaizen.board.helper;

// 1. Framework-level
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.backlog.repository.BacklogRepository;
import com.korur.oba.apps.kaizen.board.constant.BoardConstant;
import com.korur.oba.apps.kaizen.board.constant.BoardExceptionLogConstant;
import com.korur.oba.apps.kaizen.board.constant.service.BoardServiceLogConstant;
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.board.repository.BoardMemberRepository;
import com.korur.oba.apps.kaizen.board.repository.BoardMemberSpecialtyRepository;
import com.korur.oba.apps.kaizen.board.repository.BoardRepository;
import com.korur.oba.apps.kaizen.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kaizen.exception.ExceptionDomainKaizenError;
import com.korur.oba.apps.kaizen.label.repository.LabelRepository;
import com.korur.oba.apps.kaizen.milestone.service.MilestoneService;
import com.korur.oba.apps.kaizen.project.repository.ProjectRepository;
import com.korur.oba.apps.kaizen.sprint.helper.SprintLifecycleHelper;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.apps.kaizen.ticket.helper.TicketHelper;
import com.korur.oba.apps.kaizen.ticket.repository.TicketRepository;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.SlugUtil;

/**
 * Helper for board-related orchestration logic including cascade
 * delete/restore operations and board hierarchy resolution.
 *
 * Extracts complex operations from board services to keep them
 * focused on high-level workflow coordination.
 */
@Component
@RequiredArgsConstructor
public class BoardHelper {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BoardHelper.class);

    private final BoardRepository boardRepository;
    private final TicketRepository ticketRepository;
    private final BacklogRepository backlogRepository;
    private final LabelRepository labelRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final BoardMemberSpecialtyRepository boardMemberSpecialtyRepository;
    private final ProjectRepository projectRepository;
    private final TicketHelper ticketHelper;
    private final BoardMemberQueryService boardMemberQueryService;
    private final MilestoneService milestoneService;
    private final SprintLifecycleHelper sprintLifecycleHelper;
    private final LoggingService loggingService;

    /**
     * Restores all cascade-deleted children of a board: backlog, labels,
     * members with specialties, projects, board-sprint links, and tickets
     * with their children. Each board-sprint link comes back with a slug that
     * is free on the board, through
     * {@link SprintLifecycleHelper#restoreLinksOfBoard(UUID)}.
     *
     * @throws ConflictException STA_SLUG_TAKEN when a concurrent write took the
     *         slug chosen for a restored board-sprint link
     */
    public void restoreBoardChildren(UUID boardId) {
        backlogRepository.restoreByBoardId(boardId);
        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                BoardServiceLogConstant.BOARD_RESTORE_BACKLOG, boardId);

        labelRepository.restoreAllByBoardId(boardId);
        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                BoardServiceLogConstant.BOARD_RESTORE_LABELS, boardId);

        List<UUID> deletedMemberIds = boardMemberRepository.findDeletedIdsByBoardId(boardId);
        boardMemberRepository.restoreAllByBoardId(boardId);
        for (UUID memberId : deletedMemberIds) {
            boardMemberSpecialtyRepository.restoreAllByBoardMemberId(memberId);
        }
        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                BoardServiceLogConstant.BOARD_RESTORE_MEMBERS, boardId);

        projectRepository.restoreAllByBoardId(boardId);
        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                BoardServiceLogConstant.BOARD_RESTORE_PROJECTS, boardId);

        // Ticket links are not restored: board delete detached them for good.
        milestoneService.restoreBoardMilestones(boardId);

        sprintLifecycleHelper.restoreLinksOfBoard(boardId);
        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                BoardServiceLogConstant.BOARD_RESTORE_BOARD_SPRINTS, boardId);

        List<Ticket> deletedTickets = ticketRepository.findDeletedByBoardId(boardId);
        ticketRepository.restoreAllByBoardId(boardId);
        for (Ticket ticket : deletedTickets) {
            ticketHelper.restoreTicketChildren(ticket.getId());
        }
        loggingService.info(LOG_PREFIX, LogLayer.HELPER,
                BoardServiceLogConstant.BOARD_RESTORE_TICKETS, boardId);
    }

    /**
     * Resolves related boards (parent, siblings, and descendants at every depth) filtered by
     * user membership. A parent board's views span its whole subtree, because a sprint
     * shared down the board tree also runs on the grandchild boards; stopping at the direct
     * children would hide their tickets on the parent. Uses repository queries instead of
     * EAGER entity navigation to avoid unnecessary collection loading.
     *
     * @param board  the board to resolve relations for
     * @param userId the user whose membership is checked
     * @return list of related boards the user is a member of (excludes the board itself)
     */
    public List<Board> resolveRelatedBoardEntities(Board board, UUID userId) {
        Set<UUID> relatedIds = new HashSet<>();

        if (board.getParentBoard() != null) {
            UUID parentId = board.getParentBoard().getId();
            relatedIds.add(parentId);
            boardRepository.findByParentBoard_Id(parentId)
                    .forEach(sibling -> relatedIds.add(sibling.getId()));
        }

        // A descendant the user is not a member of is still walked, so a member grandchild
        // below it stays included; membership is applied once the whole subtree is known.
        Set<UUID> descendantIds = new HashSet<>();
        collectDescendantIds(board.getId(), descendantIds);
        relatedIds.addAll(descendantIds);

        relatedIds.remove(board.getId());

        // The hierarchy never crosses organizations, so the board's own organization bounds it.
        Set<UUID> memberBoardIds = new HashSet<>(
                boardMemberQueryService.getMemberBoardIds(userId, board.getOrganizationId()));
        relatedIds.retainAll(memberBoardIds);

        return boardRepository.findAllById(relatedIds);
    }

    /**
     * Collects the IDs of every board below the given board, at any depth. The visited set
     * stops the walk on a cyclic hierarchy.
     *
     * @param boardId the board whose subtree is walked
     * @param visited the IDs collected so far; receives every descendant ID
     */
    private void collectDescendantIds(UUID boardId, Set<UUID> visited) {
        for (Board child : boardRepository.findByParentBoard_Id(boardId)) {
            if (visited.add(child.getId())) {
                collectDescendantIds(child.getId(), visited);
            }
        }
    }

    /**
     * Picks the slug a board with the given name gets: the name's base slug, or the first
     * suffixed form of it that no other live board uses.
     *
     * @param name          the board name
     * @param ignoreBoardId the board whose own slug does not count as taken, or null on create
     * @return the free slug
     */
    public String nextFreeSlug(String name, UUID ignoreBoardId) {
        String base = SlugUtil.slugify(name, BoardConstant.SLUG_FALLBACK);
        return SlugUtil.firstFree(base, candidate -> ignoreBoardId == null
                ? boardRepository.existsBySlug(candidate)
                : boardRepository.existsBySlugAndIdNot(candidate, ignoreBoardId));
    }

    /**
     * Brings a board's slug in line with its current name after a rename. A name whose
     * base slug is still the base of the current slug keeps the slug, so a change of letter
     * case or punctuation does not move the board's URL; any other name gets a new free slug.
     *
     * @param board the board, with the new name already applied
     */
    public void syncSlugWithName(Board board) {
        String base = SlugUtil.slugify(board.getName(), BoardConstant.SLUG_FALLBACK);
        if (SlugUtil.sameBase(board.getSlug(), base)) {
            return;
        }
        board.setSlug(nextFreeSlug(board.getName(), board.getId()));
    }

    /**
     * Writes a board at once, so a concurrent write that took the same slug after the
     * free-slug check is caught here, inside the request that chose the slug. Only the
     * slug index is answered with a conflict; every other integrity failure goes up as it is.
     *
     * @param board the board to save, with its slug set
     * @return the saved board
     * @throws ConflictException STA_SLUG_TAKEN when the slug index refuses the write
     */
    public Board saveWithSlug(Board board) {
        try {
            return boardRepository.saveAndFlush(board);
        } catch (DataIntegrityViolationException exception) {
            throw toSlugConflict(exception, board, board.getSlug());
        }
    }

    /**
     * Makes a soft-deleted board live again. When a live board took the board's slug while
     * it was deleted, the board first gets the next free slug for its name: the slug has to
     * change while the board is still deleted, because the unique index refuses two live
     * boards with one slug.
     *
     * @param board the soft-deleted board
     * @throws ConflictException STA_SLUG_TAKEN when a concurrent write took the chosen slug
     */
    public void restoreWithFreeSlug(Board board) {
        UUID boardId = board.getId();
        String slug = board.getSlug();
        try {
            if (boardRepository.existsBySlugAndIdNot(slug, boardId)) {
                slug = nextFreeSlug(board.getName(), boardId);
                boardRepository.updateSlug(boardId, slug);
            }
            boardRepository.restoreById(boardId);
        } catch (DataIntegrityViolationException exception) {
            throw toSlugConflict(exception, board, slug);
        }
    }

    /**
     * Turns a violation of the slug index into the conflict the client can retry, after
     * logging the lost race; the conflict names the board name the slug was built from.
     * Any other integrity failure is returned unchanged for the caller to throw.
     */
    private RuntimeException toSlugConflict(DataIntegrityViolationException exception, Board board, String slug) {
        if (!SlugUtil.isSlugConflict(exception, BoardConstant.SLUG_UNIQUE_INDEX)) {
            return exception;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.HELPER,
                BoardExceptionLogConstant.BOARD_SLUG_RACE_LOST, slug, board.getId(), exception.getMostSpecificCause().getMessage());
        return new ConflictException(ExceptionDomainKaizenError.STA_SLUG_TAKEN,
                Map.of(BoardConstant.FIELD_NAME, board.getName()));
    }
}
