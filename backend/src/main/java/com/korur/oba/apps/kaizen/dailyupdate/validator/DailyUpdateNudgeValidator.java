package com.korur.oba.apps.kaizen.dailyupdate.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateExceptionLogConstant;
import com.korur.oba.apps.kaizen.dailyupdate.exception.ExceptionDomainDailyUpdateError;
import com.korur.oba.apps.kaizen.dailyupdate.helper.DailyUpdateDeadlineResolver.ReportDay;
import com.korur.oba.apps.kaizen.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateNudgeQueryService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.identity.enums.UserStatus;
import com.korur.oba.shrine.organization.entity.OrganizationMember;
import com.korur.oba.shrine.organization.enums.OrganizationMemberStatus;
import com.korur.oba.shrine.organization.service.OrganizationMemberQueryService;

/**
 * Validator for nudges: who may be nudged, and only once a day.
 *
 * A nudge reminds a member who is expected to post and has not, so its target
 * is an ACTIVE membership, an ACTIVE account, a role in
 * {@link DailyUpdateConstant#EXPECTED_POSTER_ROLES} and access to Kaizen in
 * the organization: a nudge is a Kaizen reminder, so a member who cannot open
 * Kaizen there cannot act on it. Anyone else, whether a stranger, a GUEST, a
 * disabled member or a member without Kaizen, is reported as not found, so
 * the check reveals nothing about people the sender cannot nudge.
 */
@Component
@RequiredArgsConstructor
public class DailyUpdateNudgeValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DailyUpdateNudgeValidator.class);

    private final DailyUpdateRepository dailyUpdateRepository;
    private final DailyUpdateNudgeQueryService dailyUpdateNudgeQueryService;
    private final OrganizationMemberQueryService organizationMemberQueryService;
    private final AppAccessQueryService appAccessQueryService;
    private final LoggingService loggingService;

    /**
     * Ensures the sender does not nudge themselves; the reminder is for a colleague.
     *
     * @param organizationId the organization of the nudge
     * @param sender         the member who nudges
     * @param recipientId    the member to nudge
     * @throws ValidationException DLY_VAL_001 when both are the same user
     */
    public void ensureNotSelf(UUID organizationId, User sender, UUID recipientId) {
        if (sender.getId().equals(recipientId)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_NUDGE_SELF, organizationId, recipientId);
            throw new ValidationException(ExceptionDomainDailyUpdateError.VAL_NUDGE_SELF,
                    Map.of(DailyUpdateConstant.FIELD_RECIPIENT_ID, recipientId));
        }
    }

    /**
     * Ensures the recipient is a member expected to post in the organization
     * who may open Kaizen there.
     *
     * @param organizationId the organization of the nudge
     * @param recipientId    the member to nudge
     * @throws NotFoundException DLY_NOT_001 when the user is not an ACTIVE member
     *                           with an ACTIVE account and an expected role, or
     *                           has no Kaizen access in the organization
     */
    public void ensureExpectedPoster(UUID organizationId, UUID recipientId) {
        Optional<OrganizationMember> member = organizationMemberQueryService.findMember(organizationId, recipientId);
        boolean expected = member
                .filter(found -> found.getStatus() == OrganizationMemberStatus.ACTIVE)
                .filter(found -> found.getUser().getStatus() == UserStatus.ACTIVE)
                .filter(found -> DailyUpdateConstant.EXPECTED_POSTER_ROLES.contains(found.getRole()))
                .filter(found -> appAccessQueryService.hasAppAccess(found, App.KAIZEN))
                .isPresent();
        if (!expected) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_NUDGE_RECIPIENT_NOT_FOUND, organizationId, recipientId);
            throw new NotFoundException(ExceptionDomainDailyUpdateError.NOT_NUDGE_RECIPIENT_FOUND,
                    Map.of(DailyUpdateConstant.FIELD_RECIPIENT_ID, recipientId));
        }
    }

    /**
     * Ensures the recipient has not posted an update on the day, on time or
     * late: once a member posted, there is nothing left to remind them of.
     * Any live update counts, whichever organization it was posted in, as in
     * the not-posted report.
     *
     * @param recipientId the member to nudge
     * @param day         the business day
     * @throws ValidationException DLY_VAL_002 when the member has a live update that day
     */
    public void ensureNotPosted(UUID recipientId, ReportDay day) {
        boolean posted = !dailyUpdateRepository
                .findFirstPostsByAuthors(List.of(recipientId), day.dayStart(), day.dayEnd())
                .isEmpty();
        if (posted) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_NUDGE_RECIPIENT_POSTED, recipientId, day.date());
            throw new ValidationException(ExceptionDomainDailyUpdateError.VAL_NUDGE_RECIPIENT_POSTED,
                    Map.of(DailyUpdateConstant.FIELD_RECIPIENT_ID, recipientId,
                            DailyUpdateConstant.FIELD_DAY, day.date()));
        }
    }

    /**
     * Ensures nobody nudged the recipient in the organization on the day yet.
     *
     * @param organizationId the organization of the nudge
     * @param recipientId    the member to nudge
     * @param day            the business day
     * @throws ConflictException DLY_STA_001 when a nudge exists
     */
    public void ensureNotNudged(UUID organizationId, UUID recipientId, ReportDay day) {
        if (dailyUpdateNudgeQueryService.isNudged(organizationId, recipientId, day.date())) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_NUDGE_ALREADY_SENT, organizationId, recipientId,
                    day.date());
            throw alreadyNudged(recipientId, day);
        }
    }

    /**
     * Builds the conflict of a recipient already nudged that day, for the
     * check above and for a concurrent nudge refused by the unique key.
     *
     * @param recipientId the member
     * @param day         the business day
     * @return the exception to throw
     */
    public ConflictException alreadyNudged(UUID recipientId, ReportDay day) {
        return new ConflictException(ExceptionDomainDailyUpdateError.STA_NUDGE_ALREADY_SENT,
                Map.of(DailyUpdateConstant.FIELD_RECIPIENT_ID, recipientId,
                        DailyUpdateConstant.FIELD_DAY, day.date()));
    }
}
