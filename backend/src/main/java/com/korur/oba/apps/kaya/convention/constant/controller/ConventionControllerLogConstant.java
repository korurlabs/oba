package com.korur.oba.apps.kaya.convention.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the convention controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ConventionControllerLogConstant {

    private ConventionControllerLogConstant() {
    }

    public static final String CONVENTION_LIST_REQUEST = "Received request to list the conventions of {} versions in organization {}";
    public static final String CONVENTION_LIST_COMPLETED = "Listed {} conventions in organization {}";
    public static final String CONVENTION_GET_REQUEST = "Received request to read convention {} of organization {}";
    public static final String CONVENTION_GET_COMPLETED = "Read convention {} of organization {}";
}
