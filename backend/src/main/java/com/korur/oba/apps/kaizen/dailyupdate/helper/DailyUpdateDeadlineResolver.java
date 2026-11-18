package com.korur.oba.apps.kaizen.dailyupdate.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kaizen.dailyupdate.validator.DailyUpdateValidator;
import com.korur.oba.dojo.time.BusinessCalendar;

/**
 * Resolves the day a not-posted report covers: its window, its posting
 * deadline, whether an update is expected at all, and whether the deadline
 * has passed.
 *
 * Every value is taken on the {@link BusinessCalendar}, because the posting
 * rule is set in Istanbul time for the whole team: the day runs from 00:00 to
 * the next 00:00 there, so an update posted the evening before does not count,
 * and Saturday and Sunday are the Istanbul weekend.
 */
@Component
@RequiredArgsConstructor
public class DailyUpdateDeadlineResolver {

    private final DailyUpdateValidator dailyUpdateValidator;
    private final BusinessCalendar businessCalendar;
    private final Clock clock;

    /**
     * Resolves the reported day against the current time.
     *
     * @param date reported day, nullable; null means the business day today
     * @return the resolved day
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException when the day is after today
     */
    public ReportDay resolve(LocalDate date) {
        return resolve(date, clock.instant());
    }

    /**
     * Resolves the reported day against the given time, so the rule can be
     * checked for any moment without a clock.
     *
     * @param date reported day, nullable; null means the business day of now
     * @param now  the current time
     * @return the resolved day
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException when the day is after the day of now
     */
    public ReportDay resolve(LocalDate date, Instant now) {
        LocalDate today = businessCalendar.dayOf(now);
        LocalDate reportDate = date == null ? today : date;
        dailyUpdateValidator.ensureReportDateNotInFuture(reportDate, today);

        BusinessCalendar.DayRange day = businessCalendar.dayRange(reportDate, reportDate);
        Instant deadline = reportDate.atTime(DailyUpdateConstant.POSTING_DEADLINE)
                .atZone(BusinessCalendar.ZONE)
                .toInstant();
        DayOfWeek dayOfWeek = reportDate.getDayOfWeek();
        boolean workday = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
        return new ReportDay(reportDate, day.start(), day.end(), deadline, workday, !now.isBefore(deadline));
    }

    /**
     * One reported day on the business calendar.
     *
     * @param date           the calendar day
     * @param dayStart       start of the day, inclusive
     * @param dayEnd         start of the next day, exclusive
     * @param deadline       the posting deadline of the day
     * @param workday        false on Saturday and Sunday, when no update is expected
     * @param deadlinePassed true when now is at or after the deadline
     */
    public record ReportDay(LocalDate date, Instant dayStart, Instant dayEnd, Instant deadline,
                            boolean workday, boolean deadlinePassed) {
    }
}
