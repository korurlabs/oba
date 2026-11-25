package com.korur.oba.shrine.access.repository;

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
import com.korur.oba.dojo.repository.SoftDeleteRepository;
import com.korur.oba.shrine.access.dto.projection.OrganizationAppGrantProjection;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.enums.OrganizationGroupType;

/**
 * Repository for the app grants of organizations. Derived queries skip deleted grants.
 *
 * A grant to the {@link OrganizationGroupType#EVERYONE} group never reaches a user of kind AGENT: an
 * agent opens only the apps it was granted one by one, so a grant meant for every person does not
 * widen what an agent's token can reach.
 */
@Repository
public interface AppGrantRepository extends SoftDeleteRepository<AppGrant, UUID> {

    /**
     * Lists the grants of an organization that are not deleted.
     *
     * @param organizationId the organization identifier
     * @return the grants, in no particular order
     */
    List<AppGrant> findByOrganization_Id(UUID organizationId);

    /**
     * Finds a grant that is not deleted only when it belongs to the given organization, so a grant id of
     * another organization reads as missing.
     *
     * @param id             the grant identifier
     * @param organizationId the organization identifier
     * @return the grant, or empty when it is missing, deleted or of another organization
     */
    Optional<AppGrant> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    /**
     * Finds the direct grant of an app to a user in an organization that is not deleted.
     *
     * @param organizationId the organization identifier
     * @param app            the app
     * @param userId         the user identifier
     * @return the grant, or empty when there is none
     */
    Optional<AppGrant> findByOrganization_IdAndAppAndUser_Id(UUID organizationId, App app, UUID userId);

    /**
     * Finds the grant of an app to a group that is not deleted.
     *
     * @param groupId the group identifier
     * @param app     the app
     * @return the grant, or empty when there is none
     */
    Optional<AppGrant> findByGroup_IdAndApp(UUID groupId, App app);

    /**
     * Lists the grants of a group that are not deleted.
     *
     * @param groupId the group identifier
     * @return the grants, in no particular order
     */
    List<AppGrant> findByGroup_Id(UUID groupId);

    /**
     * Lists the direct grants of a user in an organization that are not deleted.
     *
     * @param organizationId the organization identifier
     * @param userId         the user identifier
     * @return the grants, in no particular order
     */
    List<AppGrant> findByOrganization_IdAndUser_Id(UUID organizationId, UUID userId);

    /**
     * Tells whether an organization grants an app to a user through a grant that is not deleted: to the
     * user directly, to the organization's {@link OrganizationGroupType#EVERYONE} group unless the user is
     * an agent, or to a custom
     * group of the organization in which the user has a member row. This is only the grant part of the
     * access rule; the caller checks the membership status, the organization status and the role first.
     *
     * @param organizationId the organization identifier
     * @param userId         the user identifier
     * @param app            the app
     * @return true when such a grant exists
     */
    default boolean existsGrantForUser(UUID organizationId, UUID userId, App app) {
        return existsGrantForUserByAppName(organizationId, userId, app.name());
    }

    /**
     * Tells whether an organization grants an app, given by name, to a user; see
     * {@link #existsGrantForUser(UUID, UUID, App)}. The app is passed by name, because the app column is
     * a PostgreSQL enum that a native query compares as text. A grant to a deleted group or a deleted
     * member row does not count, even when the grant itself was not deleted.
     *
     * @param organizationId the organization identifier
     * @param userId         the user identifier
     * @param app            the app name
     * @return true when such a grant exists
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM shrine.organization_app_grants ag
                LEFT JOIN shrine.organization_groups g
                  ON g.id = ag.group_id
                 AND g.organization_id = ag.organization_id
                 AND g.deleted = false
                WHERE ag.organization_id = :organizationId
                  AND CAST(ag.app AS VARCHAR) = :app
                  AND ag.deleted = false
                  AND (ag.user_id = :userId
                       OR (g.type = 'EVERYONE'
                           AND NOT EXISTS (SELECT 1
                                           FROM shrine.users u
                                           WHERE u.id = :userId
                                             AND u.kind = 'AGENT'))
                       OR (g.type = 'CUSTOM'
                           AND EXISTS (SELECT 1
                                       FROM shrine.organization_group_members gm
                                       WHERE gm.group_id = g.id
                                         AND gm.user_id = :userId
                                         AND gm.deleted = false)))
            )
            """, nativeQuery = true)
    boolean existsGrantForUserByAppName(@Param("organizationId") UUID organizationId,
                                        @Param("userId") UUID userId,
                                        @Param("app") String app);

    /**
     * Finds which of the given users may open an app in an organization, in one query: the whole access
     * rule of {@link com.korur.oba.shrine.access.service.AppAccessQueryService#hasAppAccess}, for many
     * users at once. A user passes with a membership that is not deleted and ACTIVE, in an organization
     * that is not deleted and ACTIVE, with one of the given roles, and either the role OWNER or a grant
     * that {@link #existsGrantForUserByAppName} would find. The app and the roles are passed by name,
     * because their columns are PostgreSQL enums that a native query compares as text.
     *
     * @param organizationId the organization identifier
     * @param app            the app name
     * @param roles          the names of the roles at or above the app's minimum role, not empty
     * @param userIds        the users to check, not empty
     * @return the ids of the users who may open the app, in no particular order
     */
    @Query(value = """
            SELECT om.user_id
            FROM shrine.organization_members om
            JOIN shrine.organizations o
              ON o.id = om.organization_id
             AND o.deleted = false
             AND o.status = 'ACTIVE'
            WHERE om.organization_id = :organizationId
              AND om.user_id IN (:userIds)
              AND om.deleted = false
              AND om.status = 'ACTIVE'
              AND CAST(om.role AS VARCHAR) IN (:roles)
              AND (om.role = 'OWNER'
                   OR EXISTS (
                       SELECT 1
                       FROM shrine.organization_app_grants ag
                       LEFT JOIN shrine.organization_groups g
                         ON g.id = ag.group_id
                        AND g.organization_id = ag.organization_id
                        AND g.deleted = false
                       WHERE ag.organization_id = om.organization_id
                         AND CAST(ag.app AS VARCHAR) = :app
                         AND ag.deleted = false
                         AND (ag.user_id = om.user_id
                              OR (g.type = 'EVERYONE'
                                  AND NOT EXISTS (SELECT 1
                                                  FROM shrine.users u
                                                  WHERE u.id = om.user_id
                                                    AND u.kind = 'AGENT'))
                              OR (g.type = 'CUSTOM'
                                  AND EXISTS (SELECT 1
                                              FROM shrine.organization_group_members gm
                                              WHERE gm.group_id = g.id
                                                AND gm.user_id = om.user_id
                                                AND gm.deleted = false)))))
            """, nativeQuery = true)
    List<UUID> findUserIdsWithAppByAppName(@Param("organizationId") UUID organizationId,
                                           @Param("app") String app,
                                           @Param("roles") Collection<String> roles,
                                           @Param("userIds") Collection<UUID> userIds);

    /**
     * Lists, for one user, the apps that each of the given organizations grants to the user, in one query
     * whatever the number of organizations: the grants that {@link #existsGrantForUserByAppName} would find,
     * for every app and organization at once. This is only the grant part of the access rule; the caller
     * checks the membership status, the organization status, the role and the OWNER rule.
     *
     * @param userId          the user identifier
     * @param organizationIds the organizations to read, not empty
     * @return one row per organization and granted app, in no particular order
     */
    @Query(value = """
            SELECT DISTINCT ag.organization_id AS organizationId,
                   CAST(ag.app AS VARCHAR) AS app
            FROM shrine.organization_app_grants ag
            LEFT JOIN shrine.organization_groups g
              ON g.id = ag.group_id
             AND g.organization_id = ag.organization_id
             AND g.deleted = false
            WHERE ag.organization_id IN (:organizationIds)
              AND ag.deleted = false
              AND (ag.user_id = :userId
                   OR (g.type = 'EVERYONE'
                       AND NOT EXISTS (SELECT 1
                                       FROM shrine.users u
                                       WHERE u.id = :userId
                                         AND u.kind = 'AGENT'))
                   OR (g.type = 'CUSTOM'
                       AND EXISTS (SELECT 1
                                   FROM shrine.organization_group_members gm
                                   WHERE gm.group_id = g.id
                                     AND gm.user_id = :userId
                                     AND gm.deleted = false)))
            """, nativeQuery = true)
    List<OrganizationAppGrantProjection> findGrantedAppsOfUser(@Param("userId") UUID userId,
                                                               @Param("organizationIds") Collection<UUID> organizationIds);
}
