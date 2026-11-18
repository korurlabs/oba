package com.korur.oba.apps.kazan.organizationsetting.constant.controller;

/**
 * Log message constants for organization settings controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OrganizationSettingControllerLogConstant {

    private OrganizationSettingControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String SECURITY_BOARD_UPDATE_REQUEST = "Received request to update organization security board";
    public static final String SECURITY_BOARD_UPDATE_COMPLETED = "Update organization security board request completed";

    public static final String LEGACY_SECURITY_BOARD_UPDATE_REQUEST =
            "Received request to update organization security board on the platform path";
}
