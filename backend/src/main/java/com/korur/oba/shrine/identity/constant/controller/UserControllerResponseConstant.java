package com.korur.oba.shrine.identity.constant.controller;

/**
 * Purpose:
 * Holds response message constants returned by UserController.
 *
 * Bigger picture:
 * Centralizes all response strings for the user management /users
 * endpoints, keeping the controller free of inline string literals per
 * the constant usage convention.
 *
 * @docType STANDARD
 * @author Claude
 * @since 2026-03-04
 * @updated 2026-03-04
 */
public final class UserControllerResponseConstant {

    private UserControllerResponseConstant() {
    }

    public static final String RESPONSE_USERS_RETRIEVED = "Users retrieved successfully";

}
