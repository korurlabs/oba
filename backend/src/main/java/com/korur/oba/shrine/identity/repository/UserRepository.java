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
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.repository.SoftDeleteRepository;
import com.korur.oba.shrine.identity.entity.User;
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
}
