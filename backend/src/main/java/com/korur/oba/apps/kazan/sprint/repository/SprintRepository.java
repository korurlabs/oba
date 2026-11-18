package com.korur.oba.apps.kazan.sprint.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for Sprint entity database operations.
 * <p>
 * Extends SoftDeleteRepository to provide soft-delete semantics including
 * findByIdWithDeleted and restoreById alongside standard CRUD operations.
 * Defines custom query methods for sprint queries by board and status.
 */
@Repository
public interface SprintRepository extends SoftDeleteRepository<Sprint, UUID> {

    /**
     * Finds all sprints belonging to the specified board via the BoardSprint join entity.
     *
     * @param boardId ID of the board
     * @return list of sprints
     */
    List<Sprint> findByBoardSprints_Board_Id(UUID boardId);

    /**
     * Finds a sprint by board and status via the BoardSprint join entity.
     *
     * @param boardId ID of the board
     * @param status  sprint status to filter by
     * @return optional sprint if found
     */
    Optional<Sprint> findByBoardSprints_Board_IdAndStatus(UUID boardId, SprintStatus status);

    /**
     * Finds the most recently started sprint of the given status on a board. Unlike
     * {@link #findByBoardSprints_Board_IdAndStatus(UUID, SprintStatus)} it does not fail when a
     * board holds two ACTIVE sprints, which a manual start of a shared sprint can still produce;
     * a read-only listing reports one of them instead of failing as a whole.
     *
     * @param boardId ID of the board
     * @param status  sprint status to filter by
     * @return the sprint with the latest start date, if any
     */
    Optional<Sprint> findFirstByBoardSprints_Board_IdAndStatusOrderByStartDateDesc(UUID boardId, SprintStatus status);

    /**
     * Lists the live sprints of a series, the highest round first. The first
     * entry is the series' head; soft-deleted sprints are left out by the
     * entity filter but keep their round.
     *
     * @param seriesId ID of the series
     * @return the series' live sprints by round, descending
     */
    List<Sprint> findBySeries_IdOrderByRoundDesc(UUID seriesId);

    /**
     * Returns the IDs of the live sprints with the given status that are linked to any of the
     * given boards, each sprint once even when it is shared by several of them.
     *
     * Soft-deleted sprints and soft-deleted board links are excluded by their entity filters.
     *
     * @param boardIds IDs of the boards whose sprints are searched; must not be empty
     * @param status   sprint status to filter by
     * @return the distinct sprint IDs, in no particular order
     */
    @Query("""
            SELECT DISTINCT s.id
            FROM Sprint s
            JOIN s.boardSprints bs
            WHERE bs.board.id IN :boardIds
              AND s.status = :status
            """)
    List<UUID> findIdsByBoardIdsAndStatus(
            @Param("boardIds") Collection<UUID> boardIds,
            @Param("status") SprintStatus status);

    /**
     * Tells whether every board the sprint was ever linked to belongs to the organization,
     * with at least one such board. Deleted links and deleted sprints count, because a
     * sprint keeps the organization of its boards after it loses its live links, so its id
     * must not become usable by another tenant. Uses a native query because the Hibernate
     * soft-delete filter hides deleted links.
     *
     * @param sprintId       ID of the sprint
     * @param organizationId ID of the organization
     * @return true when the sprint has a link and all its links point at the organization's boards
     */
    @Query(value = """
            SELECT COUNT(*) > 0 AND COALESCE(BOOL_AND(b.organization_id = :organizationId), false)
            FROM kazan.board_sprints bs
            JOIN kazan.boards b ON b.id = bs.board_id
            WHERE bs.sprint_id = :sprintId
            """, nativeQuery = true)
    boolean isLinkedOnlyToOrganization(
            @Param("sprintId") UUID sprintId,
            @Param("organizationId") UUID organizationId);

    /**
     * Tells whether the sprint has, or once had, a link to the board. Deleted links count,
     * because deleting a sprint soft-deletes its links and a restore must still find the
     * board it was deleted from. Uses a native query for that reason.
     *
     * @param sprintId ID of the sprint
     * @param boardId  ID of the board
     * @return true when a live or deleted link joins the sprint and the board
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM kazan.board_sprints
                WHERE sprint_id = :sprintId AND board_id = :boardId)
            """, nativeQuery = true)
    boolean existsLinkWithDeleted(
            @Param("sprintId") UUID sprintId,
            @Param("boardId") UUID boardId);
}
