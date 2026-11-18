package com.korur.oba.apps.kaya.link.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Service log messages of the links between delivered Markdown files.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LinkServiceLogConstant {

    private LinkServiceLogConstant() {
    }

    public static final String SCOPE_MISSING = "Refused links of organization {}: neither versionId nor repositoryId given";
    public static final String READING_VERSION_LINKS = "Reading the links of version {} of organization {}";
    public static final String READING_RELEASE_LINKS = "Reading the links of the latest release of repository {} of organization {}";
    public static final String READING_COMPOSED_LINKS = "Reading the links of repository {} of organization {} with version {} in place";
    public static final String READING_DEPENDENCIES = "Reading the dependencies of {} in {} versions of organization {}";
    public static final String DEPENDENCY_PATH_NOT_FOUND = "None of the {} versions of organization {} delivers {}";
    public static final String DEPENDENCIES_READ = "Read {} dependencies and {} users of {} in organization {}";
    public static final String VERSION_REFUSED = "Refused links of repository {} of organization {} with version {}: {}";
}
