package com.korur.oba.shrine.access.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

// 4. Internal
import com.korur.oba.shrine.access.enums.ResourceAccessAction;
import com.korur.oba.shrine.access.enums.ResourceAccessType;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Decides whether a user may read, write or delete an entity that an endpoint names only by type and id,
 * such as the parent of an attachment.
 *
 * The entity is loaded through the app that owns it, so an absent or deleted entity answers with that app's
 * not-found error first. The entity must then belong to the request's organization, the user must hold the
 * action's role and the app there, and the type's own rules (board role, chat channel and authorship,
 * Kalkan level) must pass. Every refusal throws; nothing is returned.
 */
public interface ResourceAccessQueryService {

    /**
     * Refuses the action unless the user may perform it on the entity in the request's organization.
     *
     * A read of an entity covered by a read grant of the current thread (see {@link #withReadGrant}) passes
     * without a query.
     *
     * @param user           the authenticated user
     * @param organizationId the organization of the request, which the entity must belong to
     * @param type           the type of the entity
     * @param resourceId     the entity's id
     * @param action         what the user wants to do
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException with the owning app's code when
     *         the entity is absent or deleted, or when a type rule hides it
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException APP_SEC_002 when the entity
     *         belongs to another organization; the codes of the organization, app and type checks after it
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException when a chat message takes no
     *         files, such as a daily update or a pull request share entry
     */
    void verifyResourceAccess(User user, UUID organizationId, ResourceAccessType type, UUID resourceId,
                              ResourceAccessAction action);

    /**
     * Runs a read with the given entities already allowed: {@link #verifyResourceAccess} passes a READ of
     * them by this user without a query. Entities outside the grant are still checked one by one.
     *
     * The grant checks nothing itself. The caller has checked, in the same request, the reader's role in the
     * entities' organization and the type rules of every granted entity, as a chat page does for the
     * messages it has just read from channels the reader can read. An entity of another organization, a
     * deleted one or one the reader may not see must never be granted. A read outside a request uses
     * {@link #withCheckedReadGrant} instead.
     *
     * @param user        the reader
     * @param type        the type of the granted entities
     * @param resourceIds the granted entities
     * @param read        the read, typically one attachment list call
     * @param <T>         the result of the read
     * @return the result of the read
     */
    <T> T withReadGrant(User user, ResourceAccessType type, Collection<UUID> resourceIds, Supplier<T> read);

    /**
     * Runs {@link #withReadGrant} after checking that the reader is still an active member of the
     * organization with the type's read role and its app. For a read that runs outside a request, such as the
     * reader-neutral copy of a chat message that a realtime signal carries, which passes no role check of its
     * own.
     *
     * @param user           the reader
     * @param type           the type of the granted entities
     * @param organizationId the organization every granted entity belongs to
     * @param resourceIds    the granted entities
     * @param read           the read
     * @param <T>            the result of the read
     * @return the result of the read
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException or
     *         com.korur.oba.dojo.exception.internal.business.NotFoundException when the reader may not read
     *         the organization or open the app
     */
    <T> T withCheckedReadGrant(User user, ResourceAccessType type, UUID organizationId,
                               Collection<UUID> resourceIds, Supplier<T> read);
}
