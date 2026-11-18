package com.korur.oba.apps.kazan.milestone.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneStatus;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for Milestone entity database operations.
 *
 * Derived queries exclude soft-deleted milestones through the Hibernate
 * soft-delete filter; the native queries state deleted explicitly.
 */
@Repository
public interface MilestoneRepository extends SoftDeleteRepository<Milestone, UUID>,
        JpaSpecificationExecutor<Milestone> {

    /**
     * Finds a live milestone by board and ID.
     *
     * @param boardId ID of the board
     * @param id      ID of the milestone
     * @return the milestone if it lives on the board
     */
    Optional<Milestone> findByBoard_IdAndId(UUID boardId, UUID id);

    /**
     * Finds the live milestones of the given boards.
     *
     * @param boardIds IDs of the boards
     * @return milestones across the boards
     */
    List<Milestone> findByBoard_IdIn(List<UUID> boardIds);

    /**
     * Finds the live milestones of the given boards with the given status.
     *
     * @param boardIds IDs of the boards
     * @param status   status to filter by
     * @return matching milestones
     */
    List<Milestone> findByBoard_IdInAndStatus(List<UUID> boardIds, MilestoneStatus status);

    /**
     * Finds the IDs of the live milestones of a board.
     *
     * @param boardId ID of the board
     * @return milestone IDs
     */
    @Query(value = """
            SELECT m.id FROM kazan.milestones m
            WHERE m.board_id = :boardId AND m.deleted = false
            """, nativeQuery = true)
    List<UUID> findIdsByBoardId(@Param("boardId") UUID boardId);

    /**
     * Soft-deletes all live milestones of a board and marks them as removed by
     * the board delete, so that a board restore can tell them apart from
     * milestones deleted one by one.
     *
     * @param boardId ID of the board
     * @return number of rows updated
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kazan.milestones SET deleted = true, deleted_with_board = true
            WHERE board_id = :boardId AND deleted = false
            """, nativeQuery = true)
    int softDeleteAllByBoardId(@Param("boardId") UUID boardId);

    /**
     * Restores the milestones of a board that the board delete removed.
     *
     * Milestones deleted one by one before the board stay deleted, because
     * they were removed on purpose.
     *
     * @param boardId ID of the board
     * @return number of rows updated
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE kazan.milestones SET deleted = false, deleted_with_board = false
            WHERE board_id = :boardId AND deleted = true AND deleted_with_board = true
            """, nativeQuery = true)
    int restoreAllByBoardId(@Param("boardId") UUID boardId);
}
