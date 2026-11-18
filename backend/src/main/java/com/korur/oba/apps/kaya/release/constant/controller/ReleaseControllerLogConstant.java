package com.korur.oba.apps.kaya.release.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the release and pack order controllers.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReleaseControllerLogConstant {

    private ReleaseControllerLogConstant() {
    }

    public static final String PACKS_GET_REQUEST = "Received request to read the packs of repository {} of organization {}";
    public static final String PACKS_GET_COMPLETED = "Repository {} of organization {} composes {} packs";
    public static final String PACKS_UPDATE_REQUEST = "Received request to replace the packs of repository {} of organization {}";
    public static final String PACKS_UPDATE_COMPLETED = "Repository {} of organization {} now composes {} packs";
    public static final String PACK_REQUIRED_REQUEST = "Received request to set pack {} of organization {} required {}";
    public static final String PACK_REQUIRED_COMPLETED = "Pack {} of organization {} is required {}; {} releases";
    public static final String RELEASE_LIST_REQUEST = "Received request to list the releases of repository {} of organization {}";
    public static final String RELEASE_LIST_COMPLETED = "Listed {} releases of repository {} of organization {}";
    public static final String RELEASE_GET_REQUEST = "Received request to read release {} of repository {} of organization {}";
    public static final String RELEASE_GET_COMPLETED = "Read release {} of repository {} of organization {}";
    public static final String EFFECTIVE_REQUEST = "Received request to read the effective items of pack {} of organization {}";
    public static final String EFFECTIVE_COMPLETED = "Read {} effective items of pack {} of organization {}";
}
