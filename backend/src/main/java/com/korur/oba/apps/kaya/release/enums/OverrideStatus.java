package com.korur.oba.apps.kaya.release.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * How an override stands against the item it replaces. CURRENT when the nearest ancestor delivering
 * the item still gives it as it was at the override's base; PARENT_UPDATED when it changed since, or
 * no base is known; PARENT_REMOVED when no ancestor delivers it any more.
 */
public enum OverrideStatus {
    CURRENT,
    PARENT_UPDATED,
    PARENT_REMOVED
}
