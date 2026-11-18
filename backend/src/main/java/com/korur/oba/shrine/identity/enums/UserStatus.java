package com.korur.oba.shrine.identity.enums;

/**
 * User account lifecycle status.
 *
 * Existence is handled by SoftDeleteBaseEntity (deleted boolean).
 * This enum handles access control for living (non-deleted) entities.
 */
public enum UserStatus {
    /**
     * User has been created but hasn't completed onboarding.
     */
    PENDING_ONBOARDING,

    /**
     * User has completed onboarding and can access the system.
     */
    ACTIVE,

    /**
     * User account has been suspended by an admin.
     * The user is still visible in queries but cannot access the system.
     */
    DISABLED
}
