package com.korur.oba.apps.kaya.render.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the render services.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RenderServiceLogConstant {

    private RenderServiceLogConstant() {
    }

    // --- Reads ---
    public static final String RENDERING_VERSION = "Rendering version {} of organization {}";
    public static final String VERSION_RENDERED = "Rendered version {} of organization {}: {} files";
    public static final String RENDERING_PATH = "Rendering path {} in {} versions of organization {}";
    public static final String PATH_RENDERED = "Rendered path {} of organization {}: found in {} of {} versions";
    public static final String COUNTING_PATH_MAPPINGS = "Counting the path mapping rows of {} versions of organization {}";
    public static final String PATH_MAPPINGS_COUNTED = "Counted {} files over the path mapping rows of {} versions of organization {}";
    public static final String BLOB_MISSING = "Version {} of organization {} references blob {}, which is not stored";

    // --- Writes ---
    public static final String PARSING_VERSION = "Parsing {} files into version {} of organization {}";
    public static final String VERSION_PARSED = "Parsed version {} of organization {}: {} files stored";
    public static final String PARSING_FILE = "Parsing {} {} into version {} of organization {}";
    public static final String FILE_PARSED = "Parsed {} {} into version {} of organization {}";
    public static final String FILE_REFUSED = "Refused {} in version {} of organization {}: {}";
    public static final String FILE_SCREENED_OUT = "Screened out {} of a tree of {} files: {} {}";
}
