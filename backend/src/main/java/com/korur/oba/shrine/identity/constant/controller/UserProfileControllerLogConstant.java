package com.korur.oba.shrine.identity.constant.controller;

/**
 * Purpose:
 * Holds log message constants used by UserProfileController.
 *
 * Bigger picture:
 * Centralizes all log strings for the self-service /users/me endpoints,
 * keeping the controller free of inline string literals per the constant
 * usage convention.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-04-09
 */
public final class UserProfileControllerLogConstant {

    private UserProfileControllerLogConstant() {
    }

    public static final String USER_PROFILE_SYNC_REQUEST =
            "Received request to sync user profile from JWT";

    public static final String USER_PROFILE_SYNC_COMPLETED =
            "User profile sync request completed";

    public static final String USER_PROFILE_GET_REQUEST =
            "Received request to retrieve current user profile";

    public static final String USER_PROFILE_GET_COMPLETED =
            "Current user profile retrieval completed";

    public static final String USER_PROFILE_UPDATE_REQUEST =
            "Received request to update own profile";

    public static final String USER_PROFILE_UPDATE_COMPLETED =
            "Own profile update request completed";

    public static final String USER_PROFILE_DELETE_REQUEST =
            "Received request to disable own account";

    public static final String USER_PROFILE_DELETE_COMPLETED =
            "Own account disable request completed";

    public static final String USER_PROFILE_DEFAULT_ORGANIZATION_CLEAR_REQUEST =
            "Received request to clear own default organization";

    public static final String USER_PROFILE_DEFAULT_ORGANIZATION_CLEAR_COMPLETED =
            "Own default organization clear request completed";

    public static final String USER_PROFILE_INVITATION_DECISION_REQUEST =
            "Received request to process invitation decision";

    public static final String USER_PROFILE_INVITATION_DECISION_COMPLETED =
            "Invitation decision processing completed";
}
