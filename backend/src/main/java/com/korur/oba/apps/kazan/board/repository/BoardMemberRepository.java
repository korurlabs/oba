package com.korur.oba.apps.kazan.board.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.korur.oba.apps.kazan.board.entity.BoardMember;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for BoardMember entity database operations.
 * <p>
 * Extends SoftDeleteRepository to provide soft-delete semantics including
 * restoreById alongside standard CRUD operations.
 * Defines custom query methods for board membership queries,
 * including lookups by board, user, and combined criteria.
 */
@Repository
public interface BoardMemberRepository extends SoftDeleteRepository<BoardMember, UUID> {

    /**
     * Finds all members of the specified board.
     *
     * @param boardId ID of the board
     * @return list of board members
     */
    List<BoardMember> findByBoard_Id(UUID boardId);

    /**
     * Finds all members of any of the specified boards.
     *
     * @param boardIds IDs of the boards
     * @return list of board members
     */
    List<BoardMember> findByBoard_IdIn(Collection<UUID> boardIds);

    /**
     * Finds all board memberships for the specified user.
     *
     * @param userId ID of the user
     * @return list of board members
     */
    List<BoardMember> findByUser_Id(UUID userId);

    /**
     * Finds the user's board memberships on boards of one organization.
     *
     * @param userId         ID of the user
     * @param organizationId ID of the organization that owns the boards
     * @return list of board members
     */
    List<BoardMember> findByUser_IdAndBoard_OrganizationId(UUID userId, UUID organizationId);

    /**
     * Finds a specific board membership by board and user.
     *
     * @param boardId ID of the board
     * @param userId  ID of the user
     * @return optional board member if found
     */
    Optional<BoardMember> findByBoard_IdAndUser_Id(UUID boardId, UUID userId);

    /**
     * Checks if a membership exists for the specified board and user.
     *
     * @param boardId ID of the board
     * @param userId  ID of the user
     * @return true if the membership exists, false otherwise
     */
    boolean existsByBoard_IdAndUser_Id(UUID boardId, UUID userId);

    /**
     * Finds a soft-deleted board member by board and user using a native query
     * that bypasses Hibernate's soft-delete filter.
     *
     * @param boardId ID of the board
     * @param userId  ID of the user
     * @return optional board member if a deleted record exists
     */
    @Query(value = """
            SELECT * FROM kazan.board_members
            WHERE board_id = :boardId AND user_id = :userId AND deleted = true
            LIMIT 1
            """, nativeQuery = true)
    Optional<BoardMember> findDeletedByBoardIdAndUserId(@Param("boardId") UUID boardId,
                                                        @Param("userId") UUID userId);

    /**
     * Finds a board member by board and user including soft-deleted records.
     * Uses native query to bypass the Hibernate soft-delete filter.
     * Used for RBAC checks on deleted boards where the standard query
     * would not find the membership.
     *
     * @param boardId ID of the board
     * @param userId  ID of the user
     * @return optional board member if found (including deleted)
     */
    @Query(value = """
            SELECT * FROM kazan.board_members
            WHERE board_id = :boardId AND user_id = :userId
            LIMIT 1
            """, nativeQuery = true)
    Optional<BoardMember> findByBoardIdAndUserIdIncludingDeleted(@Param("boardId") UUID boardId,
                                                                 @Param("userId") UUID userId);

    /**
     * Restores all soft-deleted board members for the specified board.
     *
     * @param boardId ID of the board
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kazan.board_members SET deleted = false
            WHERE board_id = :boardId AND deleted = true
            """, nativeQuery = true)
    int restoreAllByBoardId(@Param("boardId") UUID boardId);

    /**
     * Finds the IDs of all soft-deleted board members for the specified board.
     *
     * @param boardId ID of the board
     * @return list of board member IDs
     */
    @Query(value = """
            SELECT id FROM kazan.board_members
            WHERE board_id = :boardId AND deleted = true
            """, nativeQuery = true)
    List<UUID> findDeletedIdsByBoardId(@Param("boardId") UUID boardId);

}
