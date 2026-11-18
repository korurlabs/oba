package com.korur.oba.shrine.access.event;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;

/**
 * Published when a user loses one app in an organization but stays a member: a grant revoked, a group
 * deleted, or the user removed from a group, and no other grant still gives the user the app. A role
 * change of the member is also a cause: a new role below the app's minimum role, or an owner who loses
 * ownership and has no grant for the app. One event is published for each user and each app the user
 * lost, and only after the write, so a user who keeps the app through another grant, or never had it,
 * gets none.
 *
 * The end of the whole organization access is a different event,
 * {@link com.korur.oba.shrine.organization.event.AccessRevokedEvent}, which covers every app at once.
 * A consumer that holds state built on one app listens to both. Consumers act after the transaction
 * commits, so a change that rolls back revokes nothing. Each consumer decides which apps concern it; the
 * publisher names the app and nothing more.
 *
 * @param userId         the user who lost the app
 * @param organizationId the organization in which the app was lost
 * @param app            the app the user lost
 */
public record AppAccessRevokedEvent(UUID userId, UUID organizationId, App app) {
}
