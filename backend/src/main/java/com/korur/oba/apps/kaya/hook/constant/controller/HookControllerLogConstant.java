package com.korur.oba.apps.kaya.hook.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the hook controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class HookControllerLogConstant {

    private HookControllerLogConstant() {
    }

    public static final String HOOK_LIST_REQUEST = "Received request to list the hooks of {} versions in organization {}";
    public static final String HOOK_LIST_COMPLETED = "Listed {} hooks in organization {}";
    public static final String HOOK_GET_REQUEST = "Received request to read hook {} of organization {}";
    public static final String HOOK_GET_COMPLETED = "Read hook {} of organization {}";
}
