package com.korur.oba.apps.kazan.dailyupdate.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateCreateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateUpdateRequest;
import com.korur.oba.apps.kazan.dailyupdate.dto.response.DailyUpdateResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for daily updates.
 */
public interface DailyUpdateService {

    /**
     * Creates an update from the user's ticket lines. Every ticket must be live
     * and on a board the user is a member of. A line's percentage is written to
     * its ticket, in the same transaction, when it differs from the ticket's
     * and the user is OWNER, ADMIN or MEMBER on the ticket's board, whether or
     * not the user is an assignee.
     *
     * The update is also posted to the chat channel of one organization, in
     * the same transaction: the request's organization when given, else the
     * user's default organization when that membership is ACTIVE, else the
     * user's first ACTIVE membership by join date. A user without one gets
     * the update saved without a channel entry.
     *
     * Posting in an organization needs the
     * {@link com.korur.oba.shrine.organization.enums.OrganizationMemberRole#MEMBER}
     * role there, or a higher one, as any chat write does: a GUEST, or a
     * member of a disabled organization, is refused and the update is not
     * saved.
     *
     * @param user           the author
     * @param organizationId the request's organization, already checked for
     *                       membership by the organization header filter; null when the request named none
     * @param request        the ticket lines, focus and questions
     * @return the saved update
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException
     *         when the user lacks the MEMBER role in the organization the update is posted in
     */
    DailyUpdateResponse createDailyUpdate(User user, UUID organizationId, DailyUpdateCreateRequest request);

    /**
     * Replaces the content of the user's own update. Kept lines keep their
     * snapshot without an access check, added tickets must be reachable, and
     * only added lines and lines whose percentage changes write progress back.
     * The update's channel entry is marked edited.
     */
    DailyUpdateResponse editDailyUpdate(User user, UUID updateId, DailyUpdateUpdateRequest request);

    /**
     * Soft-deletes the user's own update and its channel entry. Ticket
     * progress is not reverted.
     */
    void deleteDailyUpdate(User user, UUID updateId);
}
