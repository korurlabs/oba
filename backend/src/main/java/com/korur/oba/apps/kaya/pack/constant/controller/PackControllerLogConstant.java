package com.korur.oba.apps.kaya.pack.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the pack controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PackControllerLogConstant {

    private PackControllerLogConstant() {
    }

    public static final String PACK_LIST_REQUEST = "Received request to list the packs of organization {}";
    public static final String PACK_LIST_COMPLETED = "Listed {} packs of organization {}";
    public static final String PACK_GET_REQUEST = "Received request to read pack {} of organization {}";
    public static final String PACK_GET_COMPLETED = "Read pack {} of organization {} with {} repositories";
    public static final String PACK_CREATE_REQUEST = "Received request to create a pack in organization {}";
    public static final String PACK_CREATE_COMPLETED = "Created pack {} in organization {}";
    public static final String PACK_UPDATE_REQUEST = "Received request to update pack {} of organization {}";
    public static final String PACK_UPDATE_COMPLETED = "Updated pack {} of organization {}, named {}";
}
