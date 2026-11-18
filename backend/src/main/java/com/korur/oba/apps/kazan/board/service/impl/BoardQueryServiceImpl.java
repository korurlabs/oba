package com.korur.oba.apps.kazan.board.service.impl;

// 1. Framework-level
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 4. Internal
import com.korur.oba.apps.kazan.board.constant.BoardConstant;
import com.korur.oba.apps.kazan.board.constant.BoardExceptionLogConstant;
import com.korur.oba.apps.kazan.board.dto.request.BoardQueryRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardListWithChildrenResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.board.enums.BoardMemberRole;
import com.korur.oba.apps.kazan.board.helper.BoardHelper;
import com.korur.oba.apps.kazan.board.mapper.BoardMapper;
import com.korur.oba.apps.kazan.board.repository.BoardRepository;
import com.korur.oba.apps.kazan.board.service.BoardAccessService;
import com.korur.oba.apps.kazan.board.service.BoardMemberQueryService;
import com.korur.oba.apps.kazan.board.service.BoardQueryService;
import com.korur.oba.apps.kazan.board.specification.BoardSpecification;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

import static com.korur.oba.apps.kazan.board.constant.service.BoardQueryServiceLogConstant.*;

/**
 * Implementation of board read operations.
 *
 * All methods run within a read-only transaction for optimal database
 * performance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardQueryServiceImpl implements BoardQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(BoardQueryServiceImpl.class);

    private final BoardRepository boardRepository;
    private final BoardAccessService boardAccessService;
    private final BoardMemberQueryService boardMemberQueryService;
    private final BoardMapper boardMapper;
    private final BoardHelper boardHelper;
    private final LoggingService loggingService;

    @Override
    public Page<BoardListWithChildrenResponse> listBoards(User user, UUID organizationId, BoardQueryRequest request) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, FETCHING_BOARDS);

        // Fetch all boards of the organization the user is explicitly a member of (both parent and child).
        Specification<Board> spec = BoardSpecification.filterBy(request, user.getId(), organizationId);
        List<Board> allMemberBoards = boardRepository.findAll(spec);

        // Collect the IDs of boards the user is a member of for child filtering.
        Set<UUID> memberBoardIds = allMemberBoards.stream()
                .map(Board::getId)
                .collect(Collectors.toSet());

        if (Boolean.TRUE.equals(request.getIncludeChildren())) {
            // Build parent-child hierarchy: group child boards under their parents,
            // only including children the user is explicitly a member of.
            // Child boards whose parent is also in the result set are nested, not top-level.
            Set<UUID> nestedChildIds = new HashSet<>();

            for (Board board : allMemberBoards) {
                if (board.getChildBoards() != null) {
                    List<Board> accessibleChildren = board.getChildBoards().stream()
                            .filter(child -> memberBoardIds.contains(child.getId()))
                            .toList();
                    board.setChildBoards(accessibleChildren);
                    accessibleChildren.forEach(child -> nestedChildIds.add(child.getId()));
                }
            }

            // Only show boards as top-level if they are not already nested under a parent.
            List<BoardListWithChildrenResponse> topLevelBoards = allMemberBoards.stream()
                    .filter(board -> !nestedChildIds.contains(board.getId()))
                    .map(boardMapper::toListWithChildrenResponse)
                    .toList();

            Page<BoardListWithChildrenResponse> boards = paginateList(topLevelBoards, request);
            loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, BOARDS_FETCHED);
            return boards;
        }

        // No children requested: return all boards flat with children nulled out.
        List<BoardListWithChildrenResponse> flatBoards = allMemberBoards.stream()
                .map(board -> {
                    board.setChildBoards(null);
                    return boardMapper.toListWithChildrenResponse(board);
                })
                .toList();

        Page<BoardListWithChildrenResponse> boards = paginateList(flatBoards, request);
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, BOARDS_FETCHED);
        return boards;
    }

    /**
     * Paginates an in-memory list according to the given query request.
     */
    private Page<BoardListWithChildrenResponse> paginateList(
            List<BoardListWithChildrenResponse> items, BoardQueryRequest request) {
        Pageable pageable = request.toPageable();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        List<BoardListWithChildrenResponse> pageContent = start >= items.size()
                ? List.of()
                : items.subList(start, end);
        return new PageImpl<>(pageContent, pageable, items.size());
    }

    @Override
    public List<BoardListResponse> listRelatedBoards(User user, UUID organizationId, UUID boardId) {
        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, FETCHING_RELATED_BOARDS);
        Board board = boardAccessService.verifyBoardAccess(
                organizationId, boardId, user.getId(), BoardMemberRole.VIEWER);

        List<Board> relatedBoards = boardHelper.resolveRelatedBoardEntities(board, user.getId())
                .stream()
                .filter(b -> !b.getId().equals(boardId))
                .toList();

        loggingService.observe(LOG_PREFIX, LogLayer.SERVICE, RELATED_BOARDS_FETCHED);
        return relatedBoards.stream().map(boardMapper::toListResponse).toList();
    }

    @Override
    public BoardListResponse getBoardBySlug(User user, UUID organizationId, String slug) {
        // A board the user is not a member of, or one of another organization, reads as not
        // found, so its slug reveals nothing.
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId);
        Board board = boardRepository.findBySlug(slug)
                .filter(candidate -> memberBoardIds.contains(candidate.getId()))
                .orElseThrow(() -> boardNotFound(BoardConstant.FIELD_SLUG, slug));

        return boardMapper.toListResponse(board);
    }

    @Override
    public BoardListResponse getBoard(User user, UUID organizationId, UUID boardId) {
        // A non-member, or a board of another organization, gets the same not found as for a
        // missing board, so the id of a board the user may not see reveals nothing.
        List<UUID> memberBoardIds = boardMemberQueryService.getMemberBoardIds(user.getId(), organizationId);
        if (!memberBoardIds.contains(boardId)) {
            throw boardNotFound(BoardConstant.FIELD_BOARD_ID, boardId);
        }
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> boardNotFound(BoardConstant.FIELD_BOARD_ID, boardId));

        return boardMapper.toListResponse(board);
    }

    /**
     * Logs a board lookup that found nothing and builds the not-found error for it.
     *
     * @param field the name of the looked-up field
     * @param value the looked-up value
     * @return the exception to throw
     */
    private NotFoundException boardNotFound(String field, Object value) {
        loggingService.error(
                LOG_PREFIX,
                LogLayer.SERVICE,
                BoardExceptionLogConstant.BOARD_NOT_FOUND,
                value);
        return new NotFoundException(
                ExceptionDomainKazanError.NOT_BOARD_FOUND,
                Map.of(field, value));
    }

    /**
     * Runs in the caller's read-write transaction because PostgreSQL refuses
     * FOR UPDATE inside the read-only transaction of this class.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Board lockForUpdate(UUID boardId) {
        return boardRepository.lockById(boardId)
                .flatMap(boardRepository::findById)
                .orElseThrow(() -> {
                    // Log for observability before throwing the exception.
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.SERVICE,
                            BoardExceptionLogConstant.BOARD_NOT_FOUND,
                            boardId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_BOARD_FOUND,
                            Map.of(BoardConstant.FIELD_BOARD_ID, boardId));
                });
    }

}
