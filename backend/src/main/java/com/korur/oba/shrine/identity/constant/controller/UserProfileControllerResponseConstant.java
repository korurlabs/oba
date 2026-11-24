package com.korur.oba.shrine.identity.constant.controller;

/**
 * Purpose:
 * Holds response message constants returned by UserProfileController.
 *
 * Bigger picture:
 * Centralizes all response strings for the self-service /users/me
 * endpoints, keeping the controller free of inline string literals per
 * the constant usage convention.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-04-09
 */
public final class UserProfileControllerResponseConstant {

    private UserProfileControllerResponseConstant() {
    }

    public static final String RESPONSE_USER_PROFILE_SYNC_CREATED = "User profile created successfully";

    public static final String RESPONSE_USER_PROFILE_SYNC_COMPLETED = "User profile sync completed";

    public static final String RESPONSE_USER_PROFILE_RETRIEVED = "User profile retrieved successfully";

    public static final String RESPONSE_USER_PROFILE_UPDATED = "User profile updated successfully";

    public static final String RESPONSE_USER_PROFILE_DELETED = "User account disabled successfully";

    public static final String RESPONSE_INVITATION_DECISION_PROCESSED = "Invitation decision processed successfully";
}
