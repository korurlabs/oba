package com.korur.oba.apps.kaizen.dailyupdate.service.impl;

// 1. Framework-level
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateExceptionLogConstant;
import com.korur.oba.apps.kaizen.dailyupdate.constant.service.DailyUpdateServiceLogConstant;
import com.korur.oba.apps.kaizen.dailyupdate.dto.response.DailyUpdateNudgeResponse;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateNudge;
import com.korur.oba.apps.kaizen.dailyupdate.event.DailyUpdateNudgedEvent;
import com.korur.oba.apps.kaizen.dailyupdate.helper.DailyUpdateDeadlineResolver;
import com.korur.oba.apps.kaizen.dailyupdate.helper.DailyUpdateDeadlineResolver.ReportDay;
import com.korur.oba.apps.kaizen.dailyupdate.mapper.DailyUpdateNudgeMapper;
import com.korur.oba.apps.kaizen.dailyupdate.repository.DailyUpdateNudgeRepository;
import com.korur.oba.apps.kaizen.dailyupdate.service.DailyUpdateNudgeService;
import com.korur.oba.apps.kaizen.dailyupdate.validator.DailyUpdateNudgeValidator;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.identity.entity.User;
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;
import com.korur.oba.shrine.organization.service.OrganizationAccessService;

/**
 * Implementation of daily update nudge write operations.
 *
 * The daily update paths pass no organization check of the access
 * interceptor, because an author's updates span organizations, so the
 * sender's role and Kaizen access in the header's organization are checked
 * here, first.
 */
@Service
@RequiredArgsConstructor
public class DailyUpdateNudgeServiceImpl implements DailyUpdateNudgeService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DailyUpdateNudgeServiceImpl.class);

    private final DailyUpdateNudgeRepository dailyUpdateNudgeRepository;
    private final DailyUpdateNudgeValidator dailyUpdateNudgeValidator;
    private final DailyUpdateNudgeMapper dailyUpdateNudgeMapper;
    private final DailyUpdateDeadlineResolver dailyUpdateDeadlineResolver;
    private final OrganizationAccessService organizationAccessService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LoggingService loggingService;

    @Override
    @Transactional
    public DailyUpdateNudgeResponse nudge(User user, UUID organizationId, UUID recipientId) {
        organizationAccessService.verifyOrganizationAccess(organizationId, user.getId(), OrganizationMemberRole.ADMIN,
                App.KAIZEN);
        dailyUpdateNudgeValidator.ensureNotSelf(organizationId, user, recipientId);
        dailyUpdateNudgeValidator.ensureExpectedPoster(organizationId, recipientId);

        ReportDay today = dailyUpdateDeadlineResolver.resolve(null);
        dailyUpdateNudgeValidator.ensureNotPosted(recipientId, today);
        dailyUpdateNudgeValidator.ensureNotNudged(organizationId, recipientId, today);

        DailyUpdateNudge nudge = saveNudge(
                dailyUpdateNudgeMapper.toEntity(organizationId, recipientId, user.getId(), today.date()), today);

        applicationEventPublisher.publishEvent(new DailyUpdateNudgedEvent(organizationId, nudge.getId(), recipientId,
                user.getId(), dailyUpdateNudgeMapper.toSenderName(user), today.date()));

        loggingService.info(LOG_PREFIX, LogLayer.SERVICE, DailyUpdateServiceLogConstant.DAILY_UPDATE_NUDGE_CREATED,
                nudge.getId(), organizationId, recipientId, today.date());

        return dailyUpdateNudgeMapper.toResponse(nudge);
    }

    /**
     * Writes the nudge at once, so a concurrent nudge of the same recipient
     * that passed the existence check is caught here and answered with the
     * same conflict. Every other integrity failure goes up as it is.
     *
     * @throws com.korur.oba.dojo.exception.internal.business.ConflictException DLY_STA_001 when the
     *         unique key on organization, recipient and day refuses the row
     */
    private DailyUpdateNudge saveNudge(DailyUpdateNudge nudge, ReportDay today) {
        try {
            return dailyUpdateNudgeRepository.saveAndFlush(nudge);
        } catch (DataIntegrityViolationException exception) {
            if (isOncePerDayConflict(exception)) {
                loggingService.warn(LOG_PREFIX, LogLayer.SERVICE,
                        DailyUpdateExceptionLogConstant.DAILY_UPDATE_NUDGE_RACE_LOST, nudge.getOrganizationId(),
                        nudge.getRecipientId(), today.date(), exception.getMostSpecificCause().getMessage());
                throw dailyUpdateNudgeValidator.alreadyNudged(nudge.getRecipientId(), today);
            }
            throw exception;
        }
    }

    /**
     * Tells whether an integrity failure is the once-a-day unique key, and
     * not a foreign key or any other constraint of the nudge.
     */
    private static boolean isOncePerDayConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return DailyUpdateConstant.NUDGE_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
