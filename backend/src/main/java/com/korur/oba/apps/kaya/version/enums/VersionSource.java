package com.korur.oba.apps.kaya.version.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Where a version came from: the web editor, the CLI, a seed run or a restore of an older
 * version.
 */
public enum VersionSource {
    MANUAL,
    CLI,
    SEED,
    ROLLBACK
}
