package com.korur.oba.apps.kaya.reference.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the reference controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReferenceControllerLogConstant {

    private ReferenceControllerLogConstant() {
    }

    public static final String REFERENCE_GET_REQUEST = "Received request to read reference {} of organization {}";
    public static final String REFERENCE_GET_COMPLETED = "Read reference {} of organization {}";
}
