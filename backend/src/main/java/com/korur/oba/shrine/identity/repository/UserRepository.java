package com.korur.oba.shrine.identity.repository;

// 1. Framework-level
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

// 2. Third-party
import jakarta.persistence.LockModeType;

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.repository.SoftDeleteRepository;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserKind;
import com.korur.oba.shrine.identity.enums.UserStatus;

/**
 * Repository for User entity database operations.
 * <p>
 * Extends SoftDeleteRepository for soft-delete support and
 * JpaSpecificationExecutor for dynamic query building via specifications.
 * Provides lookup methods by email and Keycloak ID for authentication and identity resolution.
 */
@Repository
public interface UserRepository extends SoftDeleteRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * Finds a user by their email address using case-insensitive matching.
     *
     * @param email the email address to search for
     * @return optional containing the user if found
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Finds a user by their Keycloak ID, with the default organization loaded in the same query.
     *
     * This method uses an {@link EntityGraph} to fetch the user's default organization
     * in the same query. The organizationMemberships collection is
     * {@link jakarta.persistence.FetchType#LAZY} and is not loaded by this query.
     *
     * This is primarily used by the {@link com.korur.oba.dojo.security.annotation.GetUser}
     * annotation resolver to efficiently load authenticated user data.
     *
     * @param keycloakId the Keycloak user ID from JWT "sub" claim
     * @return an Optional containing the user if found, or empty if not found
     * @see com.korur.oba.dojo.security.annotation.GetUser
     */
    @EntityGraph(attributePaths = {"defaultOrganization"})
    Optional<User> findByKeycloakId(String keycloakId);

    /**
     * Replaces the default organization with the next available one for a user.
     * If no other active memberships exist, sets to NULL.
     * Uses a native UPDATE so the {@link User} entity and its associations are not loaded.
     */
    @Modifying
    @Query(value = """
            UPDATE shrine.users SET default_organization_id = (
                SELECT om.organization_id FROM shrine.organization_members om
                WHERE om.user_id = :userId AND om.organization_id != :organizationId AND om.deleted = false AND om.status = 'ACTIVE'
                LIMIT 1
            ) WHERE id = :userId AND default_organization_id = :organizationId
            """, nativeQuery = true)
    void reassignDefaultOrganization(@Param("userId") UUID userId, @Param("organizationId") UUID organizationId);

    /**
     * Checks whether a soft-deleted user carries the given Keycloak ID.
     *
     * Deleting a user keeps its Keycloak account and its keycloakId, so the same
     * subject can still log in. The @SoftDelete filter hides the deleted row from
     * every derived query, which is why this check reads the table natively.
     *
     * @param keycloakId the Keycloak user ID from the JWT "sub" claim
     * @return true if a soft-deleted user has this Keycloak ID
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM shrine.users u
                WHERE u.keycloak_id = :keycloakId AND u.deleted = true
            )
            """, nativeQuery = true)
    boolean existsDeletedByKeycloakId(@Param("keycloakId") String keycloakId);

    /**
     * Updates a user's status directly without loading the entity.
     */
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :userId")
    void updateUserStatus(@Param("userId") UUID userId, @Param("status") UserStatus status);

    /**
     * Loads a user and locks its row until the caller's transaction ends, so that writes an app
     * makes on one person cannot interleave.
     *
     * @param id the user identifier
     * @return the locked user, or empty when no user has the id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Lists the users of one kind that belong to an organization as agents.
     *
     * @param kind           the kind, {@link UserKind#AGENT} for agents
     * @param organizationId the organization of the agents
     * @return the live agents of the organization
     */
    List<User> findAllByKindAndAgentDetailOrganizationId(UserKind kind, UUID organizationId);

    /**
     * Finds a user of one kind by id within the organization it belongs to as an agent, so an id of a
     * person or of another organization's agent finds nothing.
     *
     * @param id             the user id
     * @param kind           the kind, {@link UserKind#AGENT} for agents
     * @param organizationId the organization of the agent
     * @return the agent, or empty
     */
    Optional<User> findByIdAndKindAndAgentDetailOrganizationId(UUID id, UserKind kind, UUID organizationId);

    /**
     * Checks whether a live agent of the organization already has the name, ignoring case, as the
     * unique index on the users table does.
     *
     * @param organizationId the organization of the agents
     * @param name           the name to check
     * @param excludeUserId  the agent being renamed, which may keep its own name; null on create
     * @return true if another live agent has the name
     */
    default boolean existsAgentName(UUID organizationId, String name, UUID excludeUserId) {
        return existsNameOfKind(UserKind.AGENT, organizationId, name, excludeUserId);
    }

    /**
     * Checks whether another live user of one kind in the organization has the name, ignoring case.
     *
     * The kind is bound as a parameter, not written as a literal: Hibernate renders an enum literal
     * as a cast to the Java type name, which names no PostgreSQL type.
     *
     * @param kind           the kind, {@link UserKind#AGENT} for agents
     * @param organizationId the organization of the agents
     * @param name           the name to check
     * @param excludeUserId  the user being renamed, which may keep its own name; null on create
     * @return true if another live user of the kind has the name
     */
    @Query("""
            SELECT COUNT(u) > 0 FROM User u
            WHERE u.kind = :kind
              AND u.agentDetail.organizationId = :organizationId
              AND lower(u.profile.firstName) = lower(:name)
              AND (:excludeUserId IS NULL OR u.id <> :excludeUserId)
            """)
    boolean existsNameOfKind(@Param("kind") UserKind kind, @Param("organizationId") UUID organizationId,
            @Param("name") String name, @Param("excludeUserId") UUID excludeUserId);
}
