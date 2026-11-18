package com.korur.oba.shrine.identity.constant.controller;

/**
 * Purpose:
 * Holds log message constants used by UserController.
 *
 * Bigger picture:
 * Centralizes all log strings for the user management /users endpoints,
 * keeping the controller free of inline string literals per the constant
 * usage convention.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-03-04
 */
public final class UserControllerLogConstant {

    private UserControllerLogConstant() {
    }

    public static final String USER_LIST_REQUEST =
            "Received request to list users";

    public static final String USER_LIST_REQUEST_COMPLETED =
            "User list request completed";

}
