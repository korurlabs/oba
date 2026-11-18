package com.korur.oba.shrine.organization.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.repository.SoftDeleteRepository;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;

/**
 * Repository for organization membership persistence and lookup.
 * Extends SoftDeleteRepository for soft-delete support.
 */
@Repository
public interface OrganizationMemberRepository extends SoftDeleteRepository<OrganizationMember, UUID> {

    /**
     * Retrieves all active (non-deleted) memberships for a given user.
     */
    List<OrganizationMember> findByUser_Id(UUID userId);

    /**
     * Retrieves memberships for a given user filtered by membership status.
     * Used by the personal dashboard to fetch only ACTIVE memberships across all of the user's organizations.
     */
    List<OrganizationMember> findByUser_IdAndStatus(UUID userId, OrganizationMemberStatus status);

    /**
     * Retrieves all active (non-deleted) memberships for a given organization.
     * The entity graph loads each member's user, with that user's default
     * organization, in the same query: the to-one associations are
     * {@link jakarta.persistence.FetchType#EAGER}, and
     * without the graph Hibernate loads every member's user with its own select.
     */
    @EntityGraph(attributePaths = {"organization", "user", "user.defaultOrganization"})
    List<OrganizationMember> findByOrganization_Id(UUID organizationId);

    /**
     * Retrieves memberships for a given organization filtered by status.
     */
    List<OrganizationMember> findByOrganization_IdAndStatus(UUID organizationId, OrganizationMemberStatus status);

    /**
     * Finds an active (non-deleted) membership entry for a specific user in an organization.
     */
    Optional<OrganizationMember> findByOrganization_IdAndUser_Id(UUID organizationId, UUID userId);

    /**
     * Finds a soft-deleted membership for a specific user in an organization.
     * Bypasses the Hibernate soft-delete filter via native query.
     */
    @Query(value = """
            SELECT * FROM shrine.organization_members
            WHERE organization_id = :orgId AND user_id = :userId AND deleted = true
            LIMIT 1
            """, nativeQuery = true)
    Optional<OrganizationMember> findDeletedByOrgIdAndUserId(@Param("orgId") UUID orgId, @Param("userId") UUID userId);

    /**
     * Finds a membership regardless of soft-delete status.
     * Bypasses the Hibernate soft-delete filter via native query.
     */
    @Query(value = """
            SELECT * FROM shrine.organization_members
            WHERE organization_id = :orgId AND user_id = :userId
            LIMIT 1
            """, nativeQuery = true)
    Optional<OrganizationMember> findByOrgIdAndUserIdIncludingDeleted(@Param("orgId") UUID orgId, @Param("userId") UUID userId);

    /**
     * Counts active owners in an organization (for sole-owner protection).
     *
     * @param orgId the organization identifier
     * @return the number of non-deleted memberships with role OWNER and status ACTIVE
     */
    default long countActiveOwners(UUID orgId) {
        return countByRoleAndStatus(orgId, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE);
    }

    /**
     * Counts the memberships of an organization with the given role and status.
     * The role and status columns are PostgreSQL enums: an enum literal in JPQL
     * is cast to a type named after the Java enum, which does not exist in the
     * database, while a bound parameter takes the column's own type.
     *
     * @param orgId  the organization identifier
     * @param role   the membership role to count
     * @param status the membership status to count
     * @return the number of matching non-deleted memberships
     */
    @Query("""
            SELECT COUNT(m) FROM OrganizationMember m
            WHERE m.organization.id = :orgId
              AND m.role = :role
              AND m.status = :status
            """)
    long countByRoleAndStatus(@Param("orgId") UUID orgId,
                              @Param("role") OrganizationMemberRole role,
                              @Param("status") OrganizationMemberStatus status);

    /**
     * Lists the ACTIVE, non-deleted members of an organization whose user account is
     * ACTIVE and not deleted, and whose membership was created before the given instant.
     * Reads only the columns of {@link OrganizationMemberSummaryProjection}, so no member's
     * {@link com.korur.oba.shrine.identity.entity.User} entity is loaded.
     *
     * @param organizationId the organization identifier
     * @param joinedBefore   memberships created at or after this instant are left out
     * @return one row per member, in no particular order
     */
    @Query(value = """
            SELECT u.id AS userId,
                   u.email AS email,
                   u.first_name AS firstName,
                   u.last_name AS lastName,
                   CAST(om.role AS VARCHAR) AS role
            FROM shrine.organization_members om
            JOIN shrine.users u ON u.id = om.user_id
            WHERE om.organization_id = :organizationId
              AND om.deleted = false
              AND om.status = 'ACTIVE'
              AND om.created_at < :joinedBefore
              AND u.deleted = false
              AND u.status = 'ACTIVE'
            """, nativeQuery = true)
    List<OrganizationMemberSummaryProjection> findActiveMemberSummaries(@Param("organizationId") UUID organizationId,
                                                                        @Param("joinedBefore") Instant joinedBefore);

    /**
     * Finds the organization a user's organization-less content belongs to: the user's
     * default organization when the membership there is
     * {@link OrganizationMemberStatus#ACTIVE} and the organization is
     * {@link com.korur.oba.shrine.organization.enums.OrganizationStatus#ACTIVE}, otherwise the active
     * organization of the user's earliest active membership (ties by organization ID). Deleted memberships and organizations never count.
     *
     * The rule is the one the daily update backfill applied to existing updates, so an
     * update posted later lands in the same channel as the author's older ones.
     *
     * @param userId the user identifier
     * @return the organization ID, or empty when the user has no active membership of an
     *         active organization
     */
    @Query(value = """
            SELECT COALESCE(
                       (SELECT o.id
                          FROM shrine.organization_members m
                          JOIN shrine.organizations o ON o.id = m.organization_id
                          JOIN shrine.users u ON u.id = m.user_id
                         WHERE m.user_id = :userId
                           AND m.organization_id = u.default_organization_id
                           AND m.status = 'ACTIVE' AND m.deleted = false
                           AND o.status = 'ACTIVE' AND o.deleted = false),
                       (SELECT o.id
                          FROM shrine.organization_members m
                          JOIN shrine.organizations o ON o.id = m.organization_id
                         WHERE m.user_id = :userId
                           AND m.status = 'ACTIVE' AND m.deleted = false
                           AND o.status = 'ACTIVE' AND o.deleted = false
                         ORDER BY m.created_at ASC, o.id ASC
                         LIMIT 1))
            """, nativeQuery = true)
    Optional<UUID> findPrimaryActiveOrganizationId(@Param("userId") UUID userId);

    /**
     * Counts the {@link OrganizationMemberStatus#ACTIVE}, non-deleted members of an organization whose
     * user account is {@link com.korur.oba.shrine.identity.enums.UserStatus#ACTIVE} and not deleted: the people who can take part in the organization now.
     *
     * @param organizationId the organization identifier
     * @return the number of such members
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM shrine.organization_members om
            JOIN shrine.users u ON u.id = om.user_id
            WHERE om.organization_id = :organizationId
              AND om.deleted = false
              AND om.status = 'ACTIVE'
              AND u.deleted = false
              AND u.status = 'ACTIVE'
            """, nativeQuery = true)
    long countActiveMembers(@Param("organizationId") UUID organizationId);

    /**
     * Finds the users holding one of the given roles through an
     * {@link OrganizationMemberStatus#ACTIVE}, non-deleted membership of an organization, with an
     * {@link com.korur.oba.shrine.identity.enums.UserStatus#ACTIVE}, non-deleted user account. The
     * roles are passed by name, because the role column is a PostgreSQL enum that a
     * native query compares as text.
     *
     * @param organizationId the organization identifier
     * @param roles          the role names, not empty
     * @return the user IDs, in no particular order
     */
    @Query(value = """
            SELECT om.user_id
            FROM shrine.organization_members om
            JOIN shrine.users u ON u.id = om.user_id
            WHERE om.organization_id = :organizationId
              AND om.deleted = false
              AND om.status = 'ACTIVE'
              AND CAST(om.role AS VARCHAR) IN (:roles)
              AND u.deleted = false
              AND u.status = 'ACTIVE'
            """, nativeQuery = true)
    List<UUID> findActiveUserIdsByRoles(@Param("organizationId") UUID organizationId,
                                        @Param("roles") Collection<String> roles);

}
