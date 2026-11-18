package com.korur.oba.shrine.organization.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal

/**
 * Published when organization access ends for a user, for every member of an organization, or
 * for one membership: a membership removed, left or disabled; a user account disabled or
 * deleted; an organization disabled or deleted.
 *
 * A null field means any, so the event names exactly the set of (user, organization) pairs whose
 * access ended. Consumers act after the transaction commits, so a change that rolls back revokes
 * nothing. The realtime layer closes the matching open sessions and drops the matching unused
 * handshake tickets, which would otherwise keep receiving signals, or open a socket, until the
 * access token behind them expired.
 *
 * @param userId         the user who lost access, or null for every member of the organization
 * @param organizationId the organization access was lost to, or null for every organization of the user
 */
public record AccessRevokedEvent(UUID userId, UUID organizationId) {

    /**
     * One membership ended: removed, left or disabled.
     *
     * @param userId         the member
     * @param organizationId the organization
     * @return the event for that one pair
     */
    public static AccessRevokedEvent member(UUID userId, UUID organizationId) {
        return new AccessRevokedEvent(userId, organizationId);
    }

    /**
     * A user account was disabled or deleted, which ends its access to every organization.
     *
     * @param userId the user
     * @return the event for every organization of the user
     */
    public static AccessRevokedEvent user(UUID userId) {
        return new AccessRevokedEvent(userId, null);
    }

    /**
     * An organization was disabled or deleted, which ends the access of every member.
     *
     * @param organizationId the organization
     * @return the event for every member of the organization
     */
    public static AccessRevokedEvent organization(UUID organizationId) {
        return new AccessRevokedEvent(null, organizationId);
    }

    /**
     * Whether a session or ticket of this user in this organization lost its access.
     *
     * @param candidateUserId         the user the session or ticket belongs to
     * @param candidateOrganizationId the organization it is bound to
     * @return true when both fields match or are null
     */
    public boolean matches(UUID candidateUserId, UUID candidateOrganizationId) {
        return (userId == null || userId.equals(candidateUserId))
                && (organizationId == null || organizationId.equals(candidateOrganizationId));
    }
}
