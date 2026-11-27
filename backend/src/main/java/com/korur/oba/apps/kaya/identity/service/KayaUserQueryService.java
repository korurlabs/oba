package com.korur.oba.apps.kaya.identity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.entity.User;

/**
 * Kaya's reads of platform users: the user of a JWT, a user by id, and the users of an
 * organization. It reads them through the platform's query services and adds the organization
 * scope every Kaya caller needs.
 */
public interface KayaUserQueryService {

    /**
     * Returns the platform user of a Keycloak subject.
     *
     * @param keycloakId the sub claim of the JWT
     * @return the user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException when no user is
     *         linked to the subject yet
     */
    User getEntityByKeycloakId(String keycloakId);

    /**
     * Looks up the platform user of a Keycloak subject.
     *
     * @param keycloakId the sub claim of the JWT
     * @return the user, or empty when no user is linked to the subject yet
     */
    Optional<User> findEntityByKeycloakId(String keycloakId);

    /**
     * Returns a user by id, for a caller that knows the user through another record, such as the
     * user a machine token belongs to, and must run the tenant check on it.
     *
     * @param userId the user's id
     * @return the user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException when no user has the id
     */
    User getEntityById(UUID userId);

    /**
     * Resolves a user that is a member of the organization, with a membership of any status, so a
     * user disabled there still resolves for the rows it owns.
     *
     * @param organizationId the organization of the request
     * @param userId         the user
     * @return the user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         user is not a member of the organization; a user of another organization answers the
     *         same as an unknown id
     */
    User requireMemberUser(UUID organizationId, UUID userId);

    /**
     * Resolves a member of the organization as {@link #requireMemberUser} does and locks the user
     * row until the caller's transaction ends. Writes on one person that must not interleave, such
     * as registering a machine and linking an account, take it first.
     *
     * @param organizationId the organization of the request
     * @param userId         the user
     * @return the locked user
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException KAY_NF_001 when the
     *         user is not a member of the organization
     * @throws org.springframework.transaction.IllegalTransactionStateException without a caller transaction
     */
    User getMemberEntityForUpdate(UUID organizationId, UUID userId);

    /**
     * Locks the user row as a foreign key check does (FOR KEY SHARE) until the caller's transaction ends,
     * for a write that inserts a row referencing the user and one of the user's machines. Retiring a
     * user's machines locks the user before the machines, and taking this first keeps the write in the
     * same order. Ordinary updates of the user are not blocked.
     *
     * @param userId the user, already known to the caller through an authenticated token
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException when no user has the id
     * @throws org.springframework.transaction.IllegalTransactionStateException without a caller transaction
     */
    void lockUserForKeyShare(UUID userId);

    /**
     * Resolves the display names of users, for lists of another domain that show who a row of the
     * organization belongs to.
     *
     * @param organizationId the organization of the rows
     * @param userIds        the users; may be empty
     * @return the display name per user id; an unknown id has no entry
     */
    Map<UUID, String> getDisplayNames(UUID organizationId, Collection<UUID> userIds);
}
