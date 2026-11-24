package com.korur.oba.shrine.identity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.identity.dto.request.UserSelfUpdateRequest;
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;
import com.korur.oba.shrine.identity.dto.response.UserSyncResult;

/**
 * Purpose:
 * Defines the write operations for JWT-based user profile synchronization.
 *
 * Bigger picture:
 * Part of the CQRS split for the user profile domain. This interface handles
 * the creation and linking of users from Keycloak JWT tokens. The read
 * counterpart is UserProfileQueryService.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-03-04
 */
public interface UserProfileService {

    /**
     * Purpose:
     * Synchronizes the authenticated Keycloak user into the local database.
     * Creates a new user if none exists, or links a keycloakId to an existing
     * email-matched user.
     *
     * Bigger picture:
     * Called by POST /users/me on every login. Idempotent — safe to call
     * repeatedly. The returned UserSyncResult carries a created flag so the
     * controller can respond with 201 or 200 accordingly.
     *
     * @docType STANDARD
     * @author Claude
     * @since 2026-03-04
     * @updated 2026-03-04
     * @return UserSyncResult containing the user detail and whether a new user was created
     */
    UserSyncResult syncUserFromJwt();

    /**
     * Updates the caller's own account. The request has no roles and no status, so
     * an account holder cannot change their own platform roles or lifecycle status.
     * Also used by onboarding to store the profile the new user entered.
     *
     * @param userId  the caller's user identifier
     * @param request the profile, preferences, email and default organization to apply
     * @return the updated account
     */
    UserDetailResponse updateProfile(UUID userId, UserSelfUpdateRequest request);

    /**
     * Disables the caller's own account. Refused while the caller is the only
     * active owner of an organization, so that organization keeps an owner.
     *
     * @param userId the caller's user identifier
     */
    void disableOwnAccount(UUID userId);
}
