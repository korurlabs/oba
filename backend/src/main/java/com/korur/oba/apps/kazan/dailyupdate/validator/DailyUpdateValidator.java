package com.korur.oba.apps.kazan.dailyupdate.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kazan.dailyupdate.constant.DailyUpdateExceptionLogConstant;
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateTicketRequest;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kazan.dailyupdate.specification.DailyUpdateSpecification;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.apps.kazan.ticket.entity.Ticket;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Validator for daily update lookups, authorship, request limits, duplicate
 * tickets, ticket access, the feed's date window and the not-posted report's day.
 *
 * The request DTOs carry the same limits as Bean Validation annotations; the
 * limit check here guards callers that build a request without passing it
 * through the controller, so a service never stores an oversized update.
 */
@Component
@RequiredArgsConstructor
public class DailyUpdateValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(DailyUpdateValidator.class);

    private final DailyUpdateRepository dailyUpdateRepository;
    private final LoggingService loggingService;

    /**
     * Ensures a live update exists and was written by one of the given users,
     * and returns it. An update the caller may not read is reported as not
     * found, so its existence is not revealed.
     *
     * @param visibleAuthorIds IDs of the users whose updates the caller may read
     * @param updateId         ID of the update
     * @return the update
     * @throws NotFoundException when the update is missing, deleted or not visible
     */
    public DailyUpdate ensureDailyUpdateVisible(Collection<UUID> visibleAuthorIds, UUID updateId) {
        return dailyUpdateRepository.findOne(DailyUpdateSpecification.visibleById(visibleAuthorIds, updateId))
                .orElseThrow(() -> {
                    loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                            DailyUpdateExceptionLogConstant.DAILY_UPDATE_NOT_FOUND, updateId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_DAILY_UPDATE_FOUND,
                            Map.of(DailyUpdateConstant.FIELD_DAILY_UPDATE_ID, updateId));
                });
    }

    /**
     * Ensures the user wrote the update; only the author may edit or delete it.
     *
     * @param dailyUpdate the update
     * @param user        the current user
     * @throws ForbiddenException when the user is not the author
     */
    public void ensureAuthor(DailyUpdate dailyUpdate, User user) {
        if (!dailyUpdate.getAuthor().getId().equals(user.getId())) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_NOT_AUTHOR, dailyUpdate.getId(), user.getId());
            throw new ForbiddenException(
                    ExceptionDomainKazanError.SEC_CANNOT_MODIFY_OTHERS_UPDATE,
                    Map.of(DailyUpdateConstant.FIELD_DAILY_UPDATE_ID, dailyUpdate.getId()));
        }
    }

    /**
     * Ensures the request stays within the update's limits: 1 to MAX_TICKETS
     * lines, each with a percentage from MIN_PERCENTAGE to MAX_PERCENTAGE and a
     * note of at most MAX_NOTE_LENGTH characters, and focus and questions of at
     * most MAX_TEXT_LENGTH characters each.
     *
     * @param lines     ticket lines of the request
     * @param focus     focus text, nullable
     * @param questions questions text, nullable
     * @throws ValidationException naming the first field that breaks a limit, with that field's limit
     */
    public void ensureWithinLimits(List<DailyUpdateTicketRequest> lines, String focus, String questions) {
        if (lines == null || lines.isEmpty() || lines.size() > DailyUpdateConstant.MAX_TICKETS) {
            throw limitExceeded(DailyUpdateConstant.FIELD_TICKETS, DailyUpdateConstant.MAX_TICKETS);
        }
        for (DailyUpdateTicketRequest line : lines) {
            if (line == null || line.ticketId() == null) {
                throw limitExceeded(DailyUpdateConstant.FIELD_TICKETS, DailyUpdateConstant.MAX_TICKETS);
            }
            Integer percentage = line.percentage();
            if (percentage == null
                    || percentage < DailyUpdateConstant.MIN_PERCENTAGE
                    || percentage > DailyUpdateConstant.MAX_PERCENTAGE) {
                throw limitExceeded(DailyUpdateConstant.FIELD_PERCENTAGE, DailyUpdateConstant.MAX_PERCENTAGE);
            }
            if (isLongerThan(line.note(), DailyUpdateConstant.MAX_NOTE_LENGTH)) {
                throw limitExceeded(DailyUpdateConstant.FIELD_NOTE, DailyUpdateConstant.MAX_NOTE_LENGTH);
            }
        }
        if (isLongerThan(focus, DailyUpdateConstant.MAX_TEXT_LENGTH)) {
            throw limitExceeded(DailyUpdateConstant.FIELD_FOCUS, DailyUpdateConstant.MAX_TEXT_LENGTH);
        }
        if (isLongerThan(questions, DailyUpdateConstant.MAX_TEXT_LENGTH)) {
            throw limitExceeded(DailyUpdateConstant.FIELD_QUESTIONS, DailyUpdateConstant.MAX_TEXT_LENGTH);
        }
    }

    /**
     * Ensures no ticket appears twice among the lines. Lines are matched to
     * stored lines by ticket, and the database allows one line per ticket.
     *
     * @param lines ticket lines of the request
     * @throws ValidationException naming the first repeated ticket
     */
    public void ensureNoDuplicateTickets(List<DailyUpdateTicketRequest> lines) {
        Set<UUID> seen = new HashSet<>();
        for (DailyUpdateTicketRequest line : lines) {
            if (!seen.add(line.ticketId())) {
                loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                        DailyUpdateExceptionLogConstant.DAILY_UPDATE_DUPLICATE_TICKET, line.ticketId());
                throw new ValidationException(
                        ExceptionDomainKazanError.VAL_DAILY_UPDATE_DUPLICATE_TICKET,
                        Map.of(DailyUpdateConstant.FIELD_TICKET_ID, line.ticketId()));
            }
        }
    }

    /**
     * Ensures every requested ticket was found among the tickets the user can
     * reach. The tickets come from TicketQueryService.getAccessibleTickets, which
     * leaves out deleted tickets and tickets on boards the user is not a member
     * of; both are reported as not found, so a foreign ticket is not revealed.
     *
     * @param requestedTicketIds IDs the request needs
     * @param foundTickets       tickets returned for those IDs
     * @throws NotFoundException naming the first missing ticket
     */
    public void ensureTicketsAccessible(Collection<UUID> requestedTicketIds, Collection<Ticket> foundTickets) {
        Set<UUID> foundIds = new HashSet<>();
        for (Ticket ticket : foundTickets) {
            foundIds.add(ticket.getId());
        }
        List<UUID> missingIds = requestedTicketIds.stream()
                .filter(ticketId -> !foundIds.contains(ticketId))
                .toList();
        if (!missingIds.isEmpty()) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_TICKETS_NOT_ACCESSIBLE, missingIds);
            throw new NotFoundException(
                    ExceptionDomainKazanError.NOT_TICKET_FOUND,
                    Map.of(DailyUpdateConstant.FIELD_TICKET_ID, missingIds.getFirst()));
        }
    }

    /**
     * Ensures the feed's date window is not inverted.
     *
     * @param from window start, nullable
     * @param to   window end, nullable
     * @throws ValidationException when from is after to
     */
    public void ensureDateRangeValid(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_FROM_AFTER_TO, from, to);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_DAILY_UPDATE_FROM_AFTER_TO,
                    Map.of(DailyUpdateConstant.FIELD_FROM, from,
                            DailyUpdateConstant.FIELD_TO, to));
        }
    }

    /**
     * Ensures the not-posted report is not asked for a day that has not begun:
     * nobody can have missed a deadline that lies in the future.
     *
     * @param date  reported day
     * @param today the current day in the report's time zone
     * @throws ValidationException when date is after today
     */
    public void ensureReportDateNotInFuture(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                    DailyUpdateExceptionLogConstant.DAILY_UPDATE_REPORT_DATE_IN_FUTURE, date, today);
            throw new ValidationException(
                    ExceptionDomainKazanError.VAL_DAILY_UPDATE_REPORT_DATE_IN_FUTURE,
                    Map.of(DailyUpdateConstant.FIELD_DATE, date,
                            DailyUpdateConstant.FIELD_TODAY, today));
        }
    }

    /**
     * Logs and builds the error of a broken limit.
     */
    private ValidationException limitExceeded(String field, int limit) {
        loggingService.error(LOG_PREFIX, LogLayer.VALIDATOR,
                DailyUpdateExceptionLogConstant.DAILY_UPDATE_LIMIT_EXCEEDED, field, limit);
        return new ValidationException(
                ExceptionValidationError.VALIDATION,
                Map.of(field, limit));
    }

    private static boolean isLongerThan(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }
}
