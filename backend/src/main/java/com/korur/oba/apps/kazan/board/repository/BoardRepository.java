package com.korur.oba.apps.kazan.board.repository;

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
import com.korur.oba.apps.kazan.board.entity.Board;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for Board entity database operations.
 * <p>
 * Extends JpaRepository to provide standard CRUD operations and
 * JpaSpecificationExecutor to support dynamic query building via specifications.
 * Defines custom query methods for board hierarchy queries.
 * <p>
 * Query methods follow Spring Data JPA naming conventions for automatic
 * implementation generation.
 */
@Repository
public interface BoardRepository extends SoftDeleteRepository<Board, UUID>,
                                         JpaSpecificationExecutor<Board> {

    /**
     * Finds all boards that are direct children of the specified parent board.
     *
     * @param parentBoardId ID of the parent board
     * @return list of child boards
     */
    List<Board> findByParentBoard_Id(UUID parentBoardId);

    /**
     * Checks whether a board with the given prefix already exists.
     *
     * @param prefix the board prefix to check
     * @return true if a board with the prefix exists
     */
    boolean existsByPrefix(String prefix);

    /**
     * Checks whether a board with the given prefix exists, excluding the specified board.
     *
     * @param prefix the board prefix to check
     * @param id     the board ID to exclude from the check
     * @return true if another board with the prefix exists
     */
    boolean existsByPrefixAndIdNot(String prefix, UUID id);

    /**
     * Checks whether a live board already uses the given slug.
     *
     * @param slug the slug to check
     * @return true if a live board has the slug
     */
    boolean existsBySlug(String slug);

    /**
     * Checks whether a live board other than the specified one uses the given slug.
     *
     * @param slug the slug to check
     * @param id   the board ID to exclude from the check
     * @return true if another live board has the slug
     */
    boolean existsBySlugAndIdNot(String slug, UUID id);

    /**
     * Finds the live board with the given slug. Live slugs are unique, so at most one
     * board matches; a soft-deleted board with the slug is never returned.
     *
     * @param slug the slug to look up, matched exactly
     * @return the live board with the slug, if any
     */
    Optional<Board> findBySlug(String slug);

    /**
     * Sets the slug of a board, deleted or not. Uses a native update because a restore
     * must change the slug of a soft-deleted board, which the Hibernate @SoftDelete filter
     * hides from entity updates, before the board becomes live again.
     *
     * @param id   the board ID
     * @param slug the new slug
     */
    @Modifying
    @Query(value = """
            UPDATE kazan.boards SET slug = :slug WHERE id = :id
            """, nativeQuery = true)
    void updateSlug(@Param("id") UUID id, @Param("slug") String slug);

    /**
     * Atomically increments the board's ticket counter by 1 and returns
     * the new value. Uses a native UPDATE … RETURNING to guarantee
     * uniqueness under concurrent load.
     *
     * @param boardId the board whose counter is incremented
     * @return the new counter value to use as the ticket number
     */
    @Query(value = """
            UPDATE kazan.boards
            SET ticket_counter = ticket_counter + 1
            WHERE id = :boardId
            RETURNING ticket_counter
            """, nativeQuery = true)
    long incrementAndGetTicketCounter(@Param("boardId") UUID boardId);

    /**
     * Finds a board by ID including soft-deleted records.
     * Uses native query to bypass the Hibernate @SoftDelete filter.
     * Safer than findByIdWithDeleted which uses getSingleResult.
     *
     * @param boardId the board ID
     * @return optional board if found (including deleted)
     */
    @Query(value = """
            SELECT * FROM kazan.boards WHERE id = :boardId
            """, nativeQuery = true)
    Optional<Board> findByIdIncludingDeleted(@Param("boardId") UUID boardId);

    /**
     * Locks the row of a board that is not deleted until the transaction
     * ends. Writers that must decide on the board's current tickets before
     * they add one take this lock first, so two concurrent writers cannot
     * both see "no ticket yet" and both create one.
     *
     * @param boardId the board to lock
     * @return the board ID, or empty when the board does not exist or is deleted
     */
    @Query(value = """
            SELECT id FROM kazan.boards
            WHERE id = :boardId AND deleted = FALSE
            FOR UPDATE
            """, nativeQuery = true)
    Optional<UUID> lockById(@Param("boardId") UUID boardId);
}
