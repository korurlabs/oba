package com.korur.oba.shrine.identity.dto.response;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Purpose:
 * Internal result from JWT sync indicating whether a new user was created.
 *
 * Bigger picture:
 * Returned by UserProfileService.syncUserFromJwt() so the controller can
 * decide between 201 Created (new user) and 200 OK (existing user) without
 * the service layer needing to know about HTTP status codes.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-03-04
 */
public record UserSyncResult(
        UserDetailResponse user,
        boolean created
) {
}
