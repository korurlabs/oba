package com.korur.oba.apps.kaizen.dailyupdate.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateNudgeResponse;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations for daily update nudges.
 */
public interface DailyUpdateNudgeService {

    /**
     * Reminds a colleague to post today's update with one push, at most once
     * per recipient, organization and business day on
     * {@link com.korur.oba.dojo.time.BusinessCalendar}, whoever sends it.
     *
     * The checks run in this order: the sender is an OWNER or ADMIN of the
     * organization, so a MEMBER or GUEST cannot nudge; the recipient is not the
     * sender; the recipient is a member expected to post; the recipient has not
     * posted today; nobody nudged them today. The nudge is saved and
     * DailyUpdateNudgedEvent published in one transaction; the push goes out
     * after the commit, and a failed push never undoes the nudge.
     *
     * @param user           the sender
     * @param organizationId the X-Organization-ID header
     * @param recipientId    the member to remind
     * @return the saved nudge
     * @throws com.korur.oba.dojo.exception.internal.auth.ForbiddenException 403 when the sender is a MEMBER
     *         or GUEST, the sender's membership is disabled, or the organization is disabled
     * @throws com.korur.oba.dojo.exception.internal.business.NotFoundException 404 when the organization is
     *         unknown or the sender is not a member, DLY_NOT_001 when the recipient is not a member expected
     *         to post
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException 422 DLY_VAL_001 for the
     *         sender themselves, DLY_VAL_002 when the recipient posted today
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException 409 DLY_STA_001 when the
     *         recipient was already nudged today, also when a concurrent nudge won the race
     */
    DailyUpdateNudgeResponse nudge(User user, UUID organizationId, UUID recipientId);
}
