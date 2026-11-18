package com.korur.oba.apps.kaya.link.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Whether the target of a link between config files is delivered by the scope it was read in. A
 * directory target is FOUND when a delivered file lies below it.
 */
public enum LinkStatus {
    FOUND,
    BROKEN
}
