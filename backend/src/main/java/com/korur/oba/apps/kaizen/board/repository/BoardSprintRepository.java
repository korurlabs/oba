package com.korur.oba.apps.kaizen.board.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
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
import com.korur.oba.apps.kaizen.board.entity.BoardSprint;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for BoardSprint entity database operations.
 * <p>
 * Extends SoftDeleteRepository to provide soft-delete semantics including
 * findByIdWithDeleted and restoreById alongside standard CRUD operations.
 * Defines custom query methods for board-sprint link lookups,
 * existence checks, and restore operations.
 */
@Repository
public interface BoardSprintRepository extends SoftDeleteRepository<BoardSprint, UUID> {

    /**
     * Finds all board-sprint links for the specified board.
     *
     * @param boardId ID of the board
     * @return list of board-sprint links
     */
    List<BoardSprint> findByBoard_Id(UUID boardId);

    /**
     * Finds the live links of a board whose sprint is live, with the sprint loaded in the
     * same query. A board restore brings back every link of the board, including links to
     * sprints deleted since, so the sprint is filtered here as the sprint list always did.
     * The order is the one the sprint lists show, since the clients keep the server order:
     * by start date, sprints without one last, then by creation time.
     *
     * @param boardId ID of the board
     * @return the board's links to live sprints, by sprint start date ascending with sprints
     *         without a start date last, then by creation time
     */
    @Query("""
            SELECT bs FROM BoardSprint bs
            JOIN FETCH bs.sprint s
            WHERE bs.board.id = :boardId AND s.deleted = false
            ORDER BY s.startDate ASC NULLS LAST, s.createdAt ASC
            """)
    List<BoardSprint> findByBoardIdWithLiveSprint(@Param("boardId") UUID boardId);

    /**
     * Checks whether a live link of the board already uses the given slug.
     *
     * @param boardId ID of the board
     * @param slug    the slug to check
     * @return true if a live link of the board has the slug
     */
    boolean existsByBoard_IdAndSlug(UUID boardId, String slug);

    /**
     * Checks whether a live link of the board, other than the specified one, uses the
     * given slug.
     *
     * @param boardId ID of the board
     * @param slug    the slug to check
     * @param id      the board-sprint link ID to exclude from the check
     * @return true if another live link of the board has the slug
     */
    boolean existsByBoard_IdAndSlugAndIdNot(UUID boardId, String slug, UUID id);

    /**
     * Checks if a board-sprint link exists for the specified board and sprint.
     *
     * @param boardId  ID of the board
     * @param sprintId ID of the sprint
     * @return true if the link exists
     */
    boolean existsByBoard_IdAndSprint_Id(UUID boardId, UUID sprintId);

    /**
     * Finds a soft-deleted board-sprint link by board and sprint.
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     *
     * @param boardId  ID of the board
     * @param sprintId ID of the sprint
     * @return optional deleted board-sprint link if found
     */
    @Query(value = """
            SELECT * FROM kaizen.board_sprints
            WHERE board_id = :boardId AND sprint_id = :sprintId AND deleted = true
            LIMIT 1
            """, nativeQuery = true)
    Optional<BoardSprint> findDeletedByBoardIdAndSprintId(
            @Param("boardId") UUID boardId,
            @Param("sprintId") UUID sprintId);

    /**
     * Finds the IDs of the soft-deleted links of a board, newest first. Two links of a board
     * that share a slug can never have been live together, so the newer one is the link that
     * held the slug last; restoring newest first lets that link keep it.
     *
     * @param boardId ID of the board
     * @return IDs of the board's soft-deleted links, newest first
     */
    @Query(value = """
            SELECT id FROM kaizen.board_sprints
            WHERE board_id = :boardId AND deleted = true
            ORDER BY created_at DESC, id
            """, nativeQuery = true)
    List<UUID> findDeletedIdsByBoardId(@Param("boardId") UUID boardId);

    /**
     * Finds the name of the sprint of a link, whether the link or the sprint is deleted or
     * not. Uses a native query because the Hibernate @SoftDelete filter hides deleted links
     * and sprints.
     *
     * @param id ID of the board-sprint link
     * @return the sprint name, or null when no link has the ID
     */
    @Query(value = """
            SELECT s.name FROM kaizen.sprints s
            JOIN kaizen.board_sprints bs ON bs.sprint_id = s.id
            WHERE bs.id = :id
            """, nativeQuery = true)
    String findSprintNameByBoardSprintId(@Param("id") UUID id);

    /**
     * Restores a soft-deleted board-sprint link with its own slug, but only when no live link
     * of its board uses that slug now and the slug is not reserved; otherwise the link stays
     * deleted, for the caller to restore with another slug. A reserved slug is refused because
     * it names a fixed board route, so a link carrying it would be unreachable by URL.
     *
     * @param id            ID of the soft-deleted board-sprint link
     * @param reservedSlugs the slugs no link may hold; must not be empty
     * @return 1 when the link was restored, 0 when its slug is taken or reserved or it is not
     *         deleted
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.board_sprints bs SET deleted = false
            WHERE bs.id = :id AND bs.deleted = true
              AND bs.slug NOT IN (:reservedSlugs)
              AND NOT EXISTS (
                  SELECT 1 FROM kaizen.board_sprints other
                  WHERE other.board_id = bs.board_id AND other.slug = bs.slug AND other.deleted = false)
            """, nativeQuery = true)
    int restoreByIdIfSlugFree(@Param("id") UUID id,
                              @Param("reservedSlugs") Collection<String> reservedSlugs);

    /**
     * Restores a soft-deleted board-sprint link and gives it the slug in the same statement.
     * The slug has to be set as the link becomes live, because the unique index on the
     * board's live slugs refuses a restored link that still carries a slug taken meanwhile.
     *
     * @param id   ID of the soft-deleted board-sprint link
     * @param slug the free slug the link gets on its board
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.board_sprints SET deleted = false, slug = :slug
            WHERE id = :id AND deleted = true
            """, nativeQuery = true)
    int restoreByIdWithSlug(@Param("id") UUID id, @Param("slug") String slug);
}
