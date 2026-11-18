package com.korur.oba.apps.kaya.identity.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Who performed an audited action on a user: a signed-in user, Kaya itself (sign-in), or a
 * recorder through its machine token.
 */
public enum UserAuditActorKind {
    USER,
    SYSTEM,
    RECORDER
}
