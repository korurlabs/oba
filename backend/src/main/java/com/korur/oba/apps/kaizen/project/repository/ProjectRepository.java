package com.korur.oba.apps.kaizen.project.repository;

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
import com.korur.oba.apps.kaizen.project.entity.Project;
import com.korur.oba.dojo.repository.SoftDeleteRepository;

/**
 * Repository interface for Project entity database operations.
 * <p>
 * Defines custom query methods for project lookups by board.
 */
@Repository
public interface ProjectRepository extends SoftDeleteRepository<Project, UUID> {

    /**
     * Finds all projects belonging to the specified board.
     *
     * @param boardId ID of the board
     * @return list of projects
     */
    List<Project> findByBoard_Id(UUID boardId);

    /**
     * Finds all projects belonging to any of the specified boards.
     *
     * @param boardIds list of board IDs
     * @return list of projects across all specified boards
     */
    List<Project> findByBoard_IdIn(List<UUID> boardIds);

    /**
     * Finds a project by board ID and project ID.
     * Used for board-scoped validation on project endpoints.
     *
     * @param boardId ID of the board
     * @param id      ID of the project
     * @return optional project if found on the specified board
     */
    Optional<Project> findByBoard_IdAndId(UUID boardId, UUID id);

    /**
     * Finds a project by ID when its board belongs to the organization.
     * Used where a ticket names a project of any board, which must stay inside the ticket
     * board's organization.
     *
     * @param id             ID of the project
     * @param organizationId ID of the organization
     * @return optional project if found on a board of the organization
     */
    Optional<Project> findByIdAndBoard_OrganizationId(UUID id, UUID organizationId);

    /**
     * Restores all soft-deleted projects for the specified board.
     *
     * @param boardId ID of the board
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.projects SET deleted = false
            WHERE board_id = :boardId AND deleted = true
            """, nativeQuery = true)
    int restoreAllByBoardId(@Param("boardId") UUID boardId);
}
