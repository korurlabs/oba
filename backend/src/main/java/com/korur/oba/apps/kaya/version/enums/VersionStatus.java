package com.korur.oba.apps.kaya.version.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Lifecycle of a version. Only an APPROVED version is ever the current version of its owner;
 * the approved version it replaces becomes SUPERSEDED.
 */
public enum VersionStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    SUPERSEDED
}
