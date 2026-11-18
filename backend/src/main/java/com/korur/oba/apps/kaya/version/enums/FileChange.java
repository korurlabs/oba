package com.korur.oba.apps.kaya.version.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * How a path differs between two versions. A change of mode or symlink target alone is MODIFIED.
 */
public enum FileChange {
    ADDED,
    MODIFIED,
    DELETED
}
