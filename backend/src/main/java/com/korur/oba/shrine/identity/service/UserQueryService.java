package com.korur.oba.shrine.identity.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.dto.UserQueryRequest;
import com.korur.oba.shrine.identity.dto.response.UserListResponse;
import com.korur.oba.shrine.identity.entity.User;

public interface UserQueryService {

    /**
     * Lists the users the caller may see: the caller and the users who are ACTIVE
     * members of an ACTIVE organization in which the caller is also an ACTIVE member.
     * The query filters narrow that set and never widen it.
     *
     * @param actorUserId the calling user's ID
     * @param query       the filters and paging
     * @return a page of visible users
     */
    Page<UserListResponse> getAllUsers(UUID actorUserId, UserQueryRequest query);

    /**
     * Retrieves the User entity by ID.
     * Used by other domains that need the entity for relational mappings.
     *
     * @param userId the user ID
     * @return the User entity
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException if user not found
     */
    User getUserEntityById(UUID userId);

    /**
     * Retrieves multiple User entities by their IDs.
     * Used by other domains that need bulk entity lookups.
     *
     * @param userIds the list of user IDs
     * @return the list of User entities
     */
    java.util.List<User> getUserEntitiesByIds(java.util.List<UUID> userIds);

    /**
     * Finds a user by email (case-insensitive).
     * Used by other domains that need to look up users by email.
     *
     * @param email the email address to search for
     * @return an Optional containing the User if found
     */
    java.util.Optional<User> findUserByEmail(String email);

    /**
     * Finds a user by the Keycloak ID that is the subject claim of their access token.
     * Used by code that reads the JWT subject outside the MVC argument resolver, and so cannot
     * use {@link com.korur.oba.dojo.security.annotation.GetUser}.
     *
     * @param keycloakId the Keycloak user ID from the JWT sub claim
     * @return an Optional containing the User if found, empty for an unknown or deleted user
     */
    java.util.Optional<User> findUserByKeycloakId(String keycloakId);

    /**
     * Loads a user and locks its row until the caller's transaction ends, with the state as
     * committed when the lock was granted. An app takes it first when its writes on one person,
     * such as registering an agent for the user, must not interleave.
     *
     * @param userId the user identifier
     * @return the locked user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException when no user has the id
     * @throws org.springframework.transaction.IllegalTransactionStateException without a caller transaction
     */
    User getUserEntityForUpdate(UUID userId);

    /**
     * Lists the live agents of an organization, ordered by name ignoring case.
     *
     * @param organizationId the organization
     * @return the agent users of the organization, disabled ones included
     */
    List<User> getAgentsOfOrganization(UUID organizationId);

    /**
     * Finds an agent of an organization. A person's id or the id of another organization's agent finds
     * nothing, so a caller cannot tell them from an unknown id.
     *
     * @param organizationId the organization the agent belongs to
     * @param userId         the agent's user id
     * @return the agent user, or empty
     */
    Optional<User> findAgent(UUID organizationId, UUID userId);

    /**
     * Checks whether another live agent of the organization has the name, ignoring case.
     *
     * @param organizationId the organization
     * @param name           the agent name
     * @param excludeUserId  the agent being renamed, or null on create
     * @return true if the name is taken
     */
    boolean existsAgentName(UUID organizationId, String name, UUID excludeUserId);
}
