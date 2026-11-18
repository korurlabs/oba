package com.korur.oba.shrine.access.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.event.AppAccessRevokedEvent;

/**
 * Tells the rest of the platform which users a grant, group or role write took an app from.
 *
 * State built on an app, such as an open realtime socket, outlives the access check that let it in. A
 * write that may take apps away therefore finds, for each app it touches, the affected users who hold
 * that app before the write ({@link AppAccessQueryService#findAppHolders}), and after the write this
 * service publishes {@link AppAccessRevokedEvent} for each user and app the user no longer holds. A user
 * who keeps the app through another grant, or who never had it, gets no event. Which apps matter to a
 * consumer is the consumer's decision; this service names every app.
 *
 * Used by {@link com.korur.oba.shrine.access.service.impl.AppAccessServiceImpl},
 * {@link com.korur.oba.shrine.access.service.impl.OrganizationGroupServiceImpl} and
 * {@link com.korur.oba.shrine.organization.service.impl.OrganizationMemberServiceImpl}.
 */
public interface AppAccessRevocationService {

    /**
     * Publishes {@link AppAccessRevokedEvent} for each user and app the user held before the write and holds
     * no longer. Call it after the write, in the same transaction, so the events reach their listeners only
     * when the write commits.
     *
     * @param organizationId the organization identifier
     * @param holdersBefore  the result of {@link AppAccessQueryService#findAppHolders} before the write
     */
    void publishLostAppAccess(UUID organizationId, Map<App, Set<UUID>> holdersBefore);
}
