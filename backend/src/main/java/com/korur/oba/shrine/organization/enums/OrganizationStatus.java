package com.korur.oba.shrine.organization.enums;

/**
 * Lifecycle state for organizations.
 *
 * Existence is handled by SoftDeleteBaseEntity (deleted boolean).
 * This enum handles access control for living (non-deleted) organizations.
 */
public enum OrganizationStatus {

    // Organization is active and available for use.
    ACTIVE,

    // Organization has been suspended by an admin. Visible but access is blocked.
    DISABLED
}
