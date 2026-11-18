package com.korur.oba.apps.kaya.identity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal

/**
 * Reads the git emails of users.
 */
public interface UserGitEmailQueryService {

    /**
     * Finds the user that a git email attributes a recorded session to in the organization: the
     * holder of the git email, or else the member whose platform sign-in email it is.
     *
     * @param organizationId the organization
     * @param email          the git email in any case; it is compared in lower case
     * @return the user id, or empty when no user of the organization holds the email
     */
    Optional<UUID> findUserIdByEmail(UUID organizationId, String email);
}
