package com.korur.oba.apps.kaizen.dailyupdate.service;

// 1. Framework-level
import org.springframework.data.domain.Page;

// 2. Third-party

// 3. Java SDK
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateMissingRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdatePreviewRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateQueryRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateMissingResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdatePreviewResponse;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kaizen.ticket.dto.response.AllTicketListResponse;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Read operations for daily updates: the feed, one update, previews, the
 * tickets suggested for a new update and the report of who has not posted.
 *
 * A user reads an update they wrote, or one whose author shares an ACTIVE
 * organization with them; any other update is reported as not found.
 */
public interface DailyUpdateQueryService {

    /**
     * Previews a new update: the lines and text a create with this request
     * would produce, and which lines would write progress back. Saves nothing.
     * The organization is resolved and checked as for a create, see
     * resolvePostingOrganizationId, so a preview fails where the create would.
     *
     * @param user           the author
     * @param organizationId the X-Organization-ID header, nullable
     * @param request        the lines and texts to preview; the focus may be empty
     * @return the preview
     */
    DailyUpdatePreviewResponse previewDailyUpdate(User user, UUID organizationId, DailyUpdatePreviewRequest request);

    /**
     * Previews an edit of the user's own update with the same rules as the
     * edit itself. Saves nothing.
     */
    DailyUpdatePreviewResponse previewDailyUpdateEdit(User user, UUID updateId, DailyUpdatePreviewRequest request);

    /**
     * Lists the tickets to preselect in a new update: assigned to the user,
     * IN_PROGRESS, on the user's boards of one organization, sub-tickets
     * included, ordered by key, at most MAX_SUGGESTED_TICKETS. The
     * organization is the header's, else the user's primary ACTIVE one; the
     * path passes no organization check of the interceptor, so the GUEST role
     * and the Kaizen app in it are checked here, once.
     *
     * @param user           the author
     * @param organizationId the X-Organization-ID header, nullable
     * @return the tickets; empty when the user has no ACTIVE organization
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException when the
     *         user is not an active member of the organization or it is disabled, and
     *         APP_SEC_001 when the user has no access to the Kaizen app there
     */
    List<AllTicketListResponse> listSuggestedTickets(User user, UUID organizationId);

    /**
     * Resolves the organization a new update is posted in, whose boards its
     * tickets must be on: the request's organization, else the author's
     * primary ACTIVE organization, the same rule that placed the updates
     * posted before the channel existed.
     *
     * The resolved organization must grant the author the MEMBER role, as for
     * any chat write: a GUEST, who cannot write in the chat, cannot post an
     * update there, and a DISABLED organization is refused with 403, so no
     * update lands in a channel nobody can read. Daily update requests pass
     * no organization role check before they reach this service, so this is
     * the only one. A GUEST of the primary organization names another
     * organization to post. The author must also have the Kaizen app there,
     * since a daily update belongs to Kaizen.
     *
     * @param user           the author
     * @param organizationId the X-Organization-ID header, nullable
     * @return the organization ID, or null when the author has no ACTIVE organization
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException when the
     *         author is not an active MEMBER or above of the organization or it is
     *         disabled, and APP_SEC_001 when the author has no access to the Kaizen app
     *         there
     */
    UUID resolvePostingOrganizationId(User user, UUID organizationId);

    /**
     * Lists the updates the user may read, newest first, filtered by author,
     * board and creation date.
     */
    Page<DailyUpdateResponse> listDailyUpdates(User user, DailyUpdateQueryRequest request);

    /**
     * Retrieves one update the user may read.
     */
    DailyUpdateResponse getDailyUpdate(User user, UUID updateId);

    /**
     * Retrieves several updates the user may read, with the same visibility and
     * per-reader canOpen as {@link #getDailyUpdate}, in a fixed number of queries
     * whatever the number of updates.
     *
     * An ID that is absent from the result belongs to a deleted update or to one
     * the user may not read; the method never throws for it. Used by other
     * domains that show updates inside a page of their own records.
     *
     * @param user      the reader
     * @param updateIds the update IDs; null or empty returns an empty map
     * @return the visible updates keyed by ID, in the order of updateIds
     */
    Map<UUID, DailyUpdateResponse> getDailyUpdatesByIds(User user, Collection<UUID> updateIds);

    /**
     * Finds a live update by ID, without a reader. Used by other domains that
     * check an update against their own rules, such as the chat, which posts
     * an update's entry only for its author and in its organization.
     *
     * @param updateId the update ID
     * @return the update, empty when it is absent or deleted
     */
    Optional<DailyUpdate> findDailyUpdate(UUID updateId);

    /**
     * Lists the members of an organization who have not posted an update by
     * the deadline of one day (10:30 Europe/Istanbul): those with no update
     * that day, then those whose first update came at or after the deadline.
     *
     * Expected are ACTIVE members with an ACTIVE account and role OWNER, ADMIN
     * or MEMBER who joined before the deadline and may open Kaizen in the
     * organization, by the rule of
     * {@link com.korur.oba.shrine.access.service.AppAccessQueryService#hasAppAccess};
     * on a Saturday or Sunday nobody is expected. A member without Kaizen is
     * neither listed nor counted, as the nudge refuses that member. Any live
     * update of a member counts, whichever organization the reader picked,
     * because updates belong to no organization. Only the organization's
     * OWNERs and ADMINs with Kaizen may read the list, and it holds posting
     * times only, never update content. Reads the members and their first
     * posts with one query each; the Kaizen check reads the membership and,
     * for a member below OWNER, its grants once for each member of an
     * expected role.
     *
     * @param user    the reader
     * @param request the organization and the day, null meaning today in Istanbul
     * @return the expected count and the members without an update before the deadline
     */
    DailyUpdateMissingResponse getMissingDailyUpdates(User user, DailyUpdateMissingRequest request);

    /**
     * Returns the tickets whose progress a line would set: the line's
     * percentage differs from the ticket's, and the user is OWNER, ADMIN or
     * MEMBER on the ticket's board. Assignment plays no part, but a VIEWER's
     * percentage stays in the update only, because writing progress is a
     * ticket edit that a VIEWER may not make. Lines whose ticket is not in
     * ticketsById are never eligible, and nothing syncs without an organization.
     *
     * @param user           the author
     * @param organizationId the organization the tickets were resolved in, nullable
     * @param lines          the request lines to consider
     * @param ticketsById    live tickets of the lines that may sync
     * @return IDs of the tickets to sync
     */
    Set<UUID> findProgressSyncTicketIds(User user, UUID organizationId, List<DailyUpdateTicketRequest> lines,
                                        Map<UUID, Ticket> ticketsById);

    /**
     * Returns the tickets whose progress an edit would set: the rule of
     * findProgressSyncTicketIds, applied only to added lines and to kept lines
     * whose percentage the edit changes. Call it before the edit is applied to
     * the stored update, whose percentages it compares against.
     *
     * @param user             the author
     * @param organizationId   the organization the tickets are resolved in, from
     *                         DailyUpdateMapperHelper.resolveTicketOrganizationId; nullable
     * @param dailyUpdate      the stored update
     * @param lines            the edit request's lines
     * @param addedTicketsById tickets of the added lines, from
     *                         DailyUpdateMapperHelper.resolveAccessibleTickets
     * @return IDs of the tickets to sync
     */
    Set<UUID> findEditProgressSyncTicketIds(User user, UUID organizationId, DailyUpdate dailyUpdate,
                                            List<DailyUpdateTicketRequest> lines,
                                            Map<UUID, Ticket> addedTicketsById);
}
