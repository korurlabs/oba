package com.korur.oba.apps.kaya.render.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the path mapping controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PathMappingControllerLogConstant {

    private PathMappingControllerLogConstant() {
    }

    public static final String PATH_MAPPING_LIST_REQUEST = "Received request to list the path mappings for {} versions in organization {}";
    public static final String PATH_MAPPING_LIST_COMPLETED = "Listed {} path mappings in organization {}";
    public static final String TOOL_MAPPING_LIST_REQUEST = "Received request to list the tool mappings for {} versions in organization {}";
    public static final String TOOL_MAPPING_LIST_COMPLETED = "Listed the reads of {} tools in organization {}";
}
