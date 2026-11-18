package com.korur.oba.apps.kaya.role.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the role controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RoleControllerLogConstant {

    private RoleControllerLogConstant() {
    }

    public static final String ROLE_LIST_REQUEST = "Received request to list the roles of {} versions in organization {}";
    public static final String ROLE_LIST_COMPLETED = "Listed {} roles in organization {}";
    public static final String ROLE_GET_REQUEST = "Received request to read role {} of organization {}";
    public static final String ROLE_GET_COMPLETED = "Read role {} of organization {}";
}
