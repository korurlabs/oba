package com.korur.oba.shrine.organization.enums;

/**
 * Defines the role of a user inside an organization.
 *
 * Each constant carries an explicit level, and access checks compare levels through
 * {@link #isAtLeast(OrganizationMemberRole)}. The declaration order is not the rank: OWNER is declared
 * first, so comparing ordinal() values would invert the hierarchy, and a reorder would silently change
 * every access rule. JPA and the PostgreSQL enum map the constants by name, so the level does not affect
 * persistence.
 */
public enum OrganizationMemberRole {

    // Highest privilege role for full organization control.
    OWNER(4),

    // Administrative role for day-to-day organization management.
    ADMIN(3),

    // Standard member role with limited permissions.
    MEMBER(2),

    // Read-only role with minimal permissions.
    GUEST(1);

    private final int level;

    OrganizationMemberRole(int level) {
        this.level = level;
    }

    /**
     * Tells whether this role grants at least the access of the given minimum role.
     *
     * @param minimum the lowest role that is allowed
     * @return true when this role's level is at least the minimum's level
     */
    public boolean isAtLeast(OrganizationMemberRole minimum) {
        return level >= minimum.level;
    }
}
