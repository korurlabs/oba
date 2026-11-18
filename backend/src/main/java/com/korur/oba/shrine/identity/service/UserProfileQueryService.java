package com.korur.oba.shrine.identity.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.identity.dto.response.UserDetailResponse;

/**
 * Purpose:
 * Defines the read operations for user profile retrieval.
 *
 * Bigger picture:
 * Part of the CQRS split for the user profile domain. This interface handles
 * read-only profile lookups using the JWT token. The write counterpart is
 * UserProfileService.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-03-04
 */
public interface UserProfileQueryService {

    /**
     * Purpose:
     * Retrieves the current authenticated user's profile without side effects.
     *
     * Bigger picture:
     * Serves the GET /users/me endpoint. If the user has not been synced
     * yet (POST /users/me was never called), throws a NotFoundException
     * to signal the client to call the sync endpoint first.
     *
     * @docType STANDARD
     * @author Claude
     * @since 2026-03-04
     * @updated 2026-03-04
     * @return the current user's detail response
     */
    UserDetailResponse getCurrentUser();
}
