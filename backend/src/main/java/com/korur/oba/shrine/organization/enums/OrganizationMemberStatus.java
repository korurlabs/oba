package com.korur.oba.shrine.organization.enums;

/**
 * Membership status for a user within an organization.
 *
 * Existence is handled by SoftDeleteBaseEntity (deleted boolean).
 * This enum handles access control for living (non-deleted) memberships.
 */
public enum OrganizationMemberStatus {
    /**
     * Active membership with access to organization resources.
     */
    ACTIVE,

    /**
     * Membership suspended by an admin.
     * The membership is still visible in queries but access is blocked.
     */
    DISABLED
}
