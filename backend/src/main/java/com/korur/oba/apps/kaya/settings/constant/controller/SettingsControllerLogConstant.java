package com.korur.oba.apps.kaya.settings.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the settings controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SettingsControllerLogConstant {

    private SettingsControllerLogConstant() {
    }

    public static final String SETTINGS_LIST_REQUEST = "Received request to list the settings of {} versions in organization {}";
    public static final String SETTINGS_LIST_COMPLETED = "Listed {} settings in organization {}";
    public static final String SETTINGS_GET_REQUEST = "Received request to read settings {} of organization {}";
    public static final String SETTINGS_GET_COMPLETED = "Read settings {} of organization {}";
}
