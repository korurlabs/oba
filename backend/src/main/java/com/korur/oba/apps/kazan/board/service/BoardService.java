package com.korur.oba.apps.kazan.board.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.dto.request.BoardAddChildrenRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardCreateRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardDeleteRequest;
import com.korur.oba.apps.kazan.board.dto.request.BoardUpdateRequest;
import com.korur.oba.apps.kazan.board.dto.response.BoardDetailResponse;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for boards and board membership.
 */
public interface BoardService {

    /**
     * Creates a new board for the current user, with a URL slug derived from its name.
     *
     * @param user    the authenticated user, who becomes OWNER
     * @param organizationId the organization of the request, which owns the board
     * @param request the board to create
     * @return the created board
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_BOARD_PREFIX_DUPLICATE} (KAZ_STA_007) when the prefix
     *         is in use, or {@link ExceptionDomainKazanError#STA_SLUG_TAKEN} (KAZ_STA_017) when a
     *         concurrent write took the chosen slug
     */
    BoardDetailResponse createBoard(User user, UUID organizationId, BoardCreateRequest request);

    /**
     * Updates board metadata. A rename moves the board to a slug derived from the new name
     * unless the current slug already has the new name's base.
     *
     * @param user    the authenticated user (must be ADMIN or OWNER)
     * @param organizationId the organization of the request, which owns the board
     * @param boardId the board to update
     * @param request the fields to change
     * @return the updated board
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_BOARD_PREFIX_DUPLICATE} (KAZ_STA_007) when the prefix
     *         is in use, or {@link ExceptionDomainKazanError#STA_SLUG_TAKEN} (KAZ_STA_017) when a
     *         concurrent write took the new slug
     */
    BoardDetailResponse updateBoard(User user, UUID organizationId, UUID boardId, BoardUpdateRequest request);

    /**
     * Deletes a board with configurable handling for child boards and tickets.
     *
     * @param user    the authenticated user (must be OWNER)
     * @param organizationId the organization of the request, which owns the board
     * @param boardId the board to delete
     * @param request delete options for tickets and child boards
     */
    void deleteBoard(User user, UUID organizationId, UUID boardId, BoardDeleteRequest request);

    /**
     * Restores a soft-deleted board and all its cascade-deleted children. When a live board
     * took the board's slug meanwhile, the restored board gets the next free slug.
     *
     * @param user    the authenticated user
     * @param organizationId the organization of the request, which owns the board
     * @param boardId the board to restore (must be deleted)
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_SLUG_TAKEN} (KAZ_STA_017) when a concurrent write
     *         took the chosen slug
     */
    void restoreBoard(User user, UUID organizationId, UUID boardId);

    /**
     * Adds one or more existing boards as children of the specified parent board.
     *
     * @param user    the authenticated user (must have manage permissions on parent and each child)
     * @param organizationId the organization of the request, which owns the board
     * @param boardId the parent board identifier
     * @param request the request containing child board IDs
     * @return the updated child boards
     */
    List<BoardDetailResponse> addChildBoards(User user, UUID organizationId, UUID boardId, BoardAddChildrenRequest request);

}
