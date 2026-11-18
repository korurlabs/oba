package com.korur.oba.apps.kazan.sprint.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintBulkLinkRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCompleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintCreateRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintDeleteRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintStartRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUnlinkRequest;
import com.korur.oba.apps.kazan.sprint.dto.request.SprintUpdateRequest;
import com.korur.oba.apps.kazan.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for sprints.
 */
public interface SprintService {

    /**
     * Creates a new sprint for a board, with a URL slug on the board derived from its name.
     *
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_SLUG_TAKEN} (KAZ_STA_017) when a concurrent
     *         write took the chosen slug on the board
     */
    SprintDetailResponse createSprint(User user, UUID organizationId, UUID boardId, SprintCreateRequest request);

    /**
     * Updates sprint metadata. A rename gives each board link a slug derived from the new
     * name, unless the link's slug already has the new name's base.
     *
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_SLUG_TAKEN} (KAZ_STA_017) when a concurrent
     *         write took a new slug on one of the sprint's boards
     */
    SprintDetailResponse updateSprint(
            User user, UUID organizationId, UUID boardId, UUID sprintId, SprintUpdateRequest request);

    /**
     * Starts a PLANNED sprint. Without takeover it starts only when no linked board
     * has another ACTIVE sprint; with takeover it first closes those sprints and
     * moves their unfinished tickets into it.
     *
     * @param request the optional start options; null equals takeover off
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_ACTIVE_SPRINT_EXISTS} (KAZ_STA_004) without
     *         takeover when a linked board has another ACTIVE sprint; errorValues.boardNames
     *         names those boards
     */
    SprintDetailResponse startSprint(
            User user, UUID organizationId, UUID boardId, UUID sprintId, SprintStartRequest request);

    /**
     * Completes a sprint, relocating incomplete tickets per the specified action.
     */
    SprintDetailResponse completeSprint(
            User user, UUID organizationId, UUID boardId, UUID sprintId, SprintCompleteRequest request);

    /**
     * Links the sprint to another board, with a slug free on that board.
     *
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_SLUG_TAKEN} (KAZ_STA_017) when a concurrent
     *         write took the chosen slug on the board
     */
    SprintDetailResponse linkSprint(User user, UUID organizationId, UUID boardId, UUID sprintId);

    /**
     * Bulk-links a sprint to multiple boards in a single transaction.
     * Boards that are already actively linked are skipped silently.
     * Soft-deleted links are restored instead of creating duplicates. Each new or restored
     * link gets a slug free on its board.
     *
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_SLUG_TAKEN} (KAZ_STA_017) when a concurrent
     *         write took a chosen slug on one of the boards
     */
    SprintDetailResponse bulkLinkSprint(
            User user, UUID organizationId, UUID boardId, UUID sprintId, SprintBulkLinkRequest request);

    /**
     * Unlinks the sprint.
     */
    void unlinkSprint(User user, UUID organizationId, UUID boardId, UUID sprintId, SprintUnlinkRequest request);

    /**
     * Deletes a sprint, handling its tickets per the specified action.
     *
     * @param request delete options for sprint tickets
     */
    void deleteSprint(User user, UUID organizationId, UUID boardId, UUID sprintId, SprintDeleteRequest request);

    /**
     * Restores a soft-deleted sprint.
     */
    void restoreSprint(User user, UUID organizationId, UUID boardId, UUID sprintId);

    /**
     * Links a sprint to a board if not already linked.
     * Validates the board is related (parent, child, or sibling) before linking.
     * Restores soft-deleted links instead of creating duplicates. The new or restored link
     * gets a slug free on the board. A sprint of another organization than the board's is
     * never linked.
     *
     * @throws NotFoundException
     *         {@link ExceptionDomainKazanError#NOT_SPRINT_FOUND} when the sprint belongs to
     *         another organization than the board
     * @throws ConflictException
     *         {@link ExceptionDomainKazanError#STA_SLUG_TAKEN} (KAZ_STA_017) when a concurrent
     *         write took the chosen slug on the board
     */
    void linkSprintToBoardIfNeeded(Sprint sprint, Board board);
}
