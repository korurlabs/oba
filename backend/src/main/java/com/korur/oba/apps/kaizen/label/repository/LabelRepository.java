package com.korur.oba.apps.kaizen.label.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.korur.oba.apps.kaizen.label.entity.Label;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal

/**
 * Repository interface for Label entity database operations.
 * <p>
 * Defines custom query methods for label lookups by board
 * and uniqueness checks.
 */
@Repository
public interface LabelRepository extends SoftDeleteRepository<Label, UUID> {

    /**
     * Finds all labels belonging to the specified board.
     *
     * @param boardId ID of the board
     * @return list of labels
     */
    List<Label> findByBoard_Id(UUID boardId);

    /**
     * Finds all labels belonging to any of the specified boards.
     *
     * @param boardIds list of board IDs
     * @return list of labels across all specified boards
     */
    List<Label> findByBoard_IdIn(List<UUID> boardIds);

    /**
     * Finds a label by board ID and label ID.
     * Used for board-scoped validation on label endpoints.
     *
     * @param boardId ID of the board
     * @param id      ID of the label
     * @return optional label if found on the specified board
     */
    Optional<Label> findByBoard_IdAndId(UUID boardId, UUID id);

    /**
     * Finds a label by ID when its board belongs to the organization.
     * Used where a ticket names a label of any board, which must stay inside the ticket
     * board's organization.
     *
     * @param id             ID of the label
     * @param organizationId ID of the organization
     * @return optional label if found on a board of the organization
     */
    Optional<Label> findByIdAndBoard_OrganizationId(UUID id, UUID organizationId);

    /**
     * Checks if a label with the given name already exists on the specified board,
     * using case-insensitive comparison.
     *
     * @param boardId ID of the board
     * @param name    label name to check
     * @return true if a label with the name exists on the board, false otherwise
     */
    boolean existsByBoard_IdAndNameIgnoreCase(UUID boardId, String name);

    /**
     * Restores all soft-deleted labels for the specified board.
     *
     * @param boardId ID of the board
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.labels SET deleted = false
            WHERE board_id = :boardId AND deleted = true
            """, nativeQuery = true)
    int restoreAllByBoardId(@Param("boardId") UUID boardId);

    /**
     * Finds a soft-deleted label by board and name (case-insensitive).
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     *
     * @param boardId ID of the board
     * @param name    label name to search for
     * @return optional deleted label if found
     */
    @Query(value = """
            SELECT * FROM kaizen.labels
            WHERE board_id = :boardId AND LOWER(name) = LOWER(:name) AND deleted = true
            LIMIT 1
            """, nativeQuery = true)
    Optional<Label> findDeletedByBoardIdAndName(
            @Param("boardId") UUID boardId,
            @Param("name") String name);
}
