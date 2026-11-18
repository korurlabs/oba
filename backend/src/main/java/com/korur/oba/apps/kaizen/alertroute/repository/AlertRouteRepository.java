package com.korur.oba.apps.kaizen.alertroute.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.JpaRepository;
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
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteBoardProjection;
import com.korur.oba.apps.kaizen.alertroute.dto.projection.AlertRouteListProjection;
import com.korur.oba.apps.kaizen.alertroute.entity.AlertRoute;

/**
 * Repository interface for {@link AlertRoute} entity database operations.
 */
@Repository
public interface AlertRouteRepository extends JpaRepository<AlertRoute, UUID> {

    /**
     * Lists the alert routes of one organization with their boards, sorted by system key.
     *
     * Native and without the board's deleted filter on purpose: a soft-deleted
     * board keeps its mappings, and the webhook refuses the alerts routed to it,
     * so the admin must still see such a route to change or remove it. Loading the entity
     * would drop the route through the board's soft-delete restriction.
     *
     * @param organizationId organization that owns the routes
     * @return the organization's alert routes in system key order
     */
    @Query(value = """
            SELECT r.id AS id,
                   r.system_key AS systemKey,
                   r.is_default AS isDefault,
                   r.updated_at AS updatedAt,
                   b.id AS boardId,
                   b.name AS boardName,
                   b.prefix AS boardPrefix,
                   b.color AS boardColor
            FROM kaizen.alert_routes r
            JOIN kaizen.boards b ON b.id = r.board_id
            WHERE r.organization_id = :organizationId
            ORDER BY r.system_key
            """, nativeQuery = true)
    List<AlertRouteListProjection> findAllWithBoard(@Param("organizationId") UUID organizationId);

    /**
     * Reads one alert route with its board, native for the same reason as
     * {@link #findAllWithBoard}: a route whose board was soft-deleted is found, so the
     * admin can repoint it to a live board. A route of another organization is not found.
     *
     * @param organizationId organization that owns the route
     * @param routeId ID of the alert route
     * @return the route with its board fields, or empty when the organization has no route with the ID
     */
    @Query(value = """
            SELECT r.id AS id,
                   r.system_key AS systemKey,
                   r.is_default AS isDefault,
                   r.updated_at AS updatedAt,
                   b.id AS boardId,
                   b.name AS boardName,
                   b.prefix AS boardPrefix,
                   b.color AS boardColor
            FROM kaizen.alert_routes r
            JOIN kaizen.boards b ON b.id = r.board_id
            WHERE r.id = :routeId
              AND r.organization_id = :organizationId
            """, nativeQuery = true)
    Optional<AlertRouteListProjection> findWithBoardById(@Param("organizationId") UUID organizationId,
                                                         @Param("routeId") UUID routeId);

    /**
     * Changes the board and the default flag of one alert route without loading
     * it, so a route whose board was soft-deleted can be repointed. The statement
     * bypasses auditing, so it sets updated_at itself. A route of another
     * organization is left unchanged.
     *
     * @param organizationId organization that owns the route
     * @param routeId ID of the alert route
     * @param boardId board that receives the system's incidents
     * @param isDefault whether the route is the default
     * @return number of rows updated
     */
    @Modifying
    @Query(value = """
            UPDATE kaizen.alert_routes
            SET board_id = :boardId,
                is_default = :isDefault,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :routeId
              AND organization_id = :organizationId
            """, nativeQuery = true)
    int updateRouteById(@Param("organizationId") UUID organizationId, @Param("routeId") UUID routeId,
                        @Param("boardId") UUID boardId, @Param("isDefault") boolean isDefault);

    /**
     * Reads the system key and board id of one alert route, native for the same
     * reason as {@link #findAllWithBoard}: a route whose board was soft-deleted is found.
     * A route of another organization is not found.
     *
     * @param organizationId organization that owns the route
     * @param routeId ID of the alert route
     * @return the route's system key and board id, or empty when the organization has no route with the ID
     */
    @Query(value = """
            SELECT system_key AS systemKey,
                   board_id AS boardId
            FROM kaizen.alert_routes
            WHERE id = :routeId
              AND organization_id = :organizationId
            """, nativeQuery = true)
    Optional<AlertRouteBoardProjection> findBoardRouteById(@Param("organizationId") UUID organizationId,
                                                           @Param("routeId") UUID routeId);

    /**
     * Deletes one alert route by id without loading it, so a route whose board
     * was soft-deleted can be removed. A route of another organization is left in place.
     *
     * @param organizationId organization that owns the route
     * @param routeId ID of the alert route
     * @return number of rows deleted
     */
    @Modifying
    @Query(value = """
            DELETE FROM kaizen.alert_routes
            WHERE id = :routeId
              AND organization_id = :organizationId
            """, nativeQuery = true)
    int deleteRouteById(@Param("organizationId") UUID organizationId, @Param("routeId") UUID routeId);

    /**
     * Checks whether a system already has an alert route in an organization.
     *
     * @param organizationId organization that owns the routes
     * @param systemKey the system key to check
     * @return true if the organization has a route for the system
     */
    boolean existsByOrganizationIdAndSystemKey(UUID organizationId, String systemKey);

    /**
     * Sets the default flag to false on every row of one organization.
     *
     * Runs as one statement before a new default is saved, so the partial unique
     * index on (organization_id) where is_default never holds two defaults of one
     * organization. Pending changes are flushed first so the statement sees them.
     *
     * @param organizationId organization whose default is cleared
     * @return number of rows updated
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE AlertRoute r SET r.isDefault = false "
            + "WHERE r.isDefault = true AND r.organizationId = :organizationId")
    int clearDefault(@Param("organizationId") UUID organizationId);

    /**
     * Reads the board id of every system of one organization.
     *
     * Native, so the board id comes from the column itself: a route to a
     * soft-deleted board stays in the map, and the alert routed to it is refused
     * when the board is resolved, instead of silently falling back to the default.
     *
     * @param organizationId organization that owns the routes
     * @return one row per alert route of the organization
     */
    @Query(value = """
            SELECT system_key AS systemKey,
                   board_id AS boardId
            FROM kaizen.alert_routes
            WHERE organization_id = :organizationId
            """, nativeQuery = true)
    List<AlertRouteBoardProjection> findBoardIdsBySystem(@Param("organizationId") UUID organizationId);

    /**
     * Reads the board id of one organization's default route, native for the same
     * reason as {@link #findBoardIdsBySystem}.
     *
     * @param organizationId organization that owns the routes
     * @return the default route's board id, or empty when no row of the organization is the default
     */
    @Query(value = """
            SELECT board_id
            FROM kaizen.alert_routes
            WHERE is_default = true
              AND organization_id = :organizationId
            """, nativeQuery = true)
    Optional<UUID> findDefaultBoardId(@Param("organizationId") UUID organizationId);
}
