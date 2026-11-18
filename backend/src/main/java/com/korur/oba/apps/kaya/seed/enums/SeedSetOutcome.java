package com.korur.oba.apps.kaya.seed.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * What a seed run did to one set: created the set with its first version, added a version to an
 * existing set, or found the set's current version equal to the imported files.
 */
public enum SeedSetOutcome {
    CREATED,
    UPDATED,
    UNCHANGED
}
