package com.korur.oba.apps.kaya.version.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The order of a version list read through
 * {@link com.korur.oba.apps.kaya.version.service.VersionQueryService#getVersions}.
 */
public enum VersionOrder {
    /** Highest version number first, the history of one pack or project. */
    NEWEST_NUMBER,
    /** Most recently changed first, so a submitted or decided version comes to the top. */
    RECENTLY_CHANGED
}
