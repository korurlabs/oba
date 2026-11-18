package com.korur.oba.apps.kazan.board.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.request.BoardQueryRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardListResponse;
import com.korur.oba.apps.kazan.board.dto.response.BoardListWithChildrenResponse;
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for boards including listing, single board lookups by id or
 * slug, and related board lookups.
 */
public interface BoardQueryService {

    /**
     * Lists the boards of the organization the current user is a member of, filtered by
     * the given query.
     *
     * @param user    the authenticated user
     * @param organizationId the organization of the request; boards of other organizations read as not found
     * @param request the query parameters including pagination and filters
     * @return paginated list of boards with optional child hierarchy
     */
    Page<BoardListWithChildrenResponse> listBoards(User user, UUID organizationId, BoardQueryRequest request);

    /**
     * Lists related boards (parent, siblings, children) for the given board,
     * excluding the board itself, filtered by user membership.
     *
     * @param user    the authenticated user
     * @param organizationId the organization of the request; boards of other organizations read as not found
     * @param boardId the board to resolve relations for
     * @return list of related boards the user is a member of
     */
    List<BoardListResponse> listRelatedBoards(User user, UUID organizationId, UUID boardId);

    /**
     * Resolves a live board by its URL slug among the boards of the organization the user is a
     * member of.
     *
     * A board the user is not a member of reads as not found, so a caller cannot tell an
     * existing board from a missing one.
     *
     * @param user the authenticated user
     * @param organizationId the organization of the request; boards of other organizations read as not found
     * @param slug the exact slug of the board
     * @return the board
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_BOARD_FOUND when no live board has the slug or the user is not a member of it
     */
    BoardListResponse getBoardBySlug(User user, UUID organizationId, String slug);

    /**
     * Returns a live board the user is a member of.
     *
     * A board the user is not a member of reads as not found, so a caller cannot tell an
     * existing board from a missing one.
     *
     * @param user    the authenticated user
     * @param organizationId the organization of the request; boards of other organizations read as not found
     * @param boardId the board identifier
     * @return the board
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         NOT_BOARD_FOUND when the board is absent, deleted, or the user is not a member of it
     */
    BoardListResponse getBoard(User user, UUID organizationId, UUID boardId);

    /**
     * Locks the board row until the caller's transaction ends and returns the
     * board. Must run inside a read-write transaction of the caller.
     *
     * @param boardId the board to lock
     * @return the locked board
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException
     *         when the board does not exist or is deleted
     */
    Board lockForUpdate(UUID boardId);

}
