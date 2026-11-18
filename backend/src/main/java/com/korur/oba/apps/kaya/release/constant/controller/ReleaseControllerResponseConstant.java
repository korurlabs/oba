package com.korur.oba.apps.kaya.release.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the release and pack order controllers.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReleaseControllerResponseConstant {

    private ReleaseControllerResponseConstant() {
    }

    public static final String RESPONSE_PACKS_RETRIEVED = "Repository packs retrieved successfully";
    public static final String RESPONSE_PACKS_UPDATED = "Repository packs updated successfully";
    public static final String RESPONSE_PACK_REQUIRED_SET = "Pack required flag set successfully";
    public static final String RESPONSE_RELEASES_RETRIEVED = "Releases retrieved successfully";
    public static final String RESPONSE_RELEASE_RETRIEVED = "Release retrieved successfully";
    public static final String RESPONSE_EFFECTIVE_RETRIEVED = "Effective items retrieved successfully";
}
