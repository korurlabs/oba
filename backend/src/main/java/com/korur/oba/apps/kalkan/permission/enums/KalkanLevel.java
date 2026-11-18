package com.korur.oba.apps.kalkan.permission.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Access level on a root page, inherited by its whole subtree. Each level includes the ones
 * before it: EDIT can read, MANAGE can edit and also changes the root's permissions.
 */
public enum KalkanLevel {
    VIEW,
    EDIT,
    MANAGE;

    /**
     * Whether this level grants at least the given one.
     *
     * @param minimum the level an action needs
     * @return true when this level is the same as or above the minimum
     */
    public boolean isAtLeast(KalkanLevel minimum) {
        return compareTo(minimum) >= 0;
    }
}
