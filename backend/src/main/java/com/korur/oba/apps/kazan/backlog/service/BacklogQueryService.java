package com.korur.oba.apps.kazan.backlog.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.dto.response.BacklogDetailResponse;
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read service interface for backlog queries.
 * <p>
 * Provides read-only access to backlog data within a board.
 */
public interface BacklogQueryService {

    /**
     * Retrieves the backlog for the specified board.
     *
     * @param user           the authenticated user requesting the backlog
     * @param organizationId the organization of the request, already checked by the interceptor
     * @param boardId        the board identifier
     * @return the backlog detail including its tickets
     */
    BacklogDetailResponse getBacklog(User user, UUID organizationId, UUID boardId);

    /**
     * Retrieves the Backlog entity by board ID.
     * Used by other domains that need the entity for relational mappings.
     *
     * @param boardId the board identifier
     * @return optional backlog entity if found
     */
    Optional<Backlog> findBacklogEntityByBoardId(UUID boardId);
}
