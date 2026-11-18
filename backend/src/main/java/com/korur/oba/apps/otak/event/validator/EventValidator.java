package com.korur.oba.apps.otak.event.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.event.constant.EventConstant;
import com.korur.oba.apps.otak.event.constant.EventExceptionLogConstant;
import com.korur.oba.apps.otak.event.entity.Event;
import com.korur.oba.apps.otak.event.enums.EventPeriod;
import com.korur.oba.apps.otak.event.repository.EventRepository;
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for event lookups, the date range, the name rule and the period filter.
 */
@Component
@RequiredArgsConstructor
public class EventValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(EventValidator.class);

    private final EventRepository eventRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the event and returns it. An event of another
     * organization answers the same not found as a missing one, so its id reveals nothing
     * to another tenant.
     *
     * @param organizationId the organization the caller acts in
     * @param eventId        the event identifier
     * @return the event
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_EVENT_FOUND} if the
     *         organization has no event with the id
     */
    public Event ensureEventExists(UUID organizationId, UUID eventId) {
        return eventRepository.findByIdAndOrganizationId(eventId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            EventExceptionLogConstant.EVENT_NOT_FOUND,
                            eventId,
                            organizationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_EVENT_FOUND,
                            Map.of(EventConstant.FIELD_EVENT_ID, eventId));
                });
    }

    /**
     * Ensures the end date is not before the start date, the same rule the database check
     * constraint enforces. A one-day event (no end date) always passes.
     *
     * @param startDate the first day of the event
     * @param endDate   the last day of the event, or null for a one-day event
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_EVENT_DATE_RANGE} when
     *         the end date is before the start date
     */
    public void ensureDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return;
        }
        if (endDate.isBefore(startDate)) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    EventExceptionLogConstant.EVENT_DATE_RANGE_INVALID, startDate, endDate);
            throw new ValidationException(
                    ExceptionDomainOtakError.VAL_EVENT_DATE_RANGE,
                    Map.of(EventConstant.FIELD_START_DATE, startDate,
                            EventConstant.FIELD_END_DATE, endDate));
        }
    }

    /**
     * Ensures no other event of the organization has the name, case ignored, so an event
     * can be picked by its name without ambiguity.
     *
     * @param organizationId the organization that owns the events
     * @param name           the trimmed name to check
     * @param exceptEventId  the event that may keep its own name on an update, or null on create
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_EVENT_NAME_TAKEN} when
     *         another event of the organization has the name
     */
    public void ensureNameFree(UUID organizationId, String name, UUID exceptEventId) {
        boolean taken = exceptEventId == null
                ? eventRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)
                : eventRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, name, exceptEventId);
        if (taken) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    EventExceptionLogConstant.EVENT_NAME_TAKEN, organizationId, name);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_EVENT_NAME_TAKEN,
                    Map.of(EventConstant.FIELD_NAME, name));
        }
    }

    /**
     * Ensures a period filter comes with the viewer's calendar day. The server cannot
     * know the viewer's day, and reading it in UTC would move an event between the tabs
     * around midnight.
     *
     * @param period the requested period, or null for every event
     * @param today  the viewer's calendar day, or null
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_EVENT_PERIOD_WITHOUT_TODAY}
     *         when a period is given without today
     */
    public void ensurePeriodHasToday(EventPeriod period, LocalDate today) {
        if (period != null && today == null) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    EventExceptionLogConstant.EVENT_PERIOD_WITHOUT_TODAY, period);
            throw new ValidationException(
                    ExceptionDomainOtakError.VAL_EVENT_PERIOD_WITHOUT_TODAY,
                    Map.of(EventConstant.FIELD_PERIOD, period));
        }
    }
}
