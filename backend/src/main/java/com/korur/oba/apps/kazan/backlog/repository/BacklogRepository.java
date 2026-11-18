package com.korur.oba.apps.kazan.backlog.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.backlog.entity.Backlog;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for Backlog entity database operations.
 * <p>
 * Defines custom query methods for backlog lookups by board.
 */
@Repository
public interface BacklogRepository extends SoftDeleteRepository<Backlog, UUID> {

    /**
     * Finds the backlog associated with the specified board.
     *
     * @param boardId ID of the board
     * @return optional backlog if found
     */
    Optional<Backlog> findByBoard_Id(UUID boardId);

    /**
     * Finds all backlogs associated with the specified boards.
     *
     * @param boardIds list of board IDs
     * @return list of backlogs for the given boards
     */
    List<Backlog> findByBoard_IdIn(List<UUID> boardIds);

    /**
     * Finds a backlog by board and ID.
     *
     * @param boardId  ID of the board
     * @param backlogId ID of the backlog
     * @return optional backlog if found
     */
    Optional<Backlog> findByBoard_IdAndId(UUID boardId, UUID backlogId);

    /**
     * Restores the soft-deleted backlog for the specified board.
     *
     * @param boardId ID of the board
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kazan.backlogs SET deleted = false
            WHERE board_id = :boardId AND deleted = true
            """, nativeQuery = true)
    int restoreByBoardId(@Param("boardId") UUID boardId);
}
