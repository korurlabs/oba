package com.korur.oba.apps.kaya.pack.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the pack controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PackControllerResponseConstant {

    private PackControllerResponseConstant() {
    }

    public static final String RESPONSE_PACKS_RETRIEVED = "Packs retrieved successfully";
    public static final String RESPONSE_PACK_RETRIEVED = "Pack retrieved successfully";
    public static final String RESPONSE_PACK_CREATED = "Pack created successfully";
    public static final String RESPONSE_PACK_UPDATED = "Pack updated successfully";
}
