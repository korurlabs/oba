package com.korur.oba.apps.kaya.identity.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The actions on a user that the audit log records.
 *
 * CREATED, RENAMED, USER_LINKED, MERGED_INTO and MERGED_FROM are no longer written. They stay so
 * the entries carried over from the former author log remain readable.
 */
public enum UserAuditAction {
    CREATED,
    RENAMED,
    USER_LINKED,
    EMAIL_ADDED,
    EMAIL_REMOVED,
    ACCOUNT_ADDED,
    ACCOUNT_REMOVED,
    MACHINE_CREATED,
    MACHINE_RETIRED,
    TOKEN_ISSUED,
    TOKEN_ROTATED,
    TOKEN_REVOKED,
    ALL_REVOKED,
    MERGED_INTO,
    MERGED_FROM
}
