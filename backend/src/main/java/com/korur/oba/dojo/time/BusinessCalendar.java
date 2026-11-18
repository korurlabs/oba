package com.korur.oba.dojo.time;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

// 4. Internal

/**
 * The team's business calendar: the days of the team rules, taken in
 * {@link #ZONE}.
 *
 * The team works in Istanbul, so the daily-update report day, its posting
 * deadline, the sprint burndown and the chat day filter use the Istanbul day,
 * for every organization and whatever the reader's own zone. No other rule
 * uses this calendar: every other backend "today" is the UTC day of the
 * injected {@link Clock}, and the viewer's day exists only in the clients.
 *
 * The class also holds the zone of the scheduled jobs, {@link #SCHEDULER_ZONE_ID},
 * so that a job author finds it next to the business zone. The jobs run in UTC,
 * not on this calendar.
 */
@Component
@RequiredArgsConstructor
public class BusinessCalendar {

    /**
     * IANA name of the business zone, as a compile-time constant so that an
     * annotation attribute such as a scheduled job's zone can take it.
     */
    public static final String ZONE_ID = "Europe/Istanbul";

    /**
     * The business zone: Istanbul, where the team works. Its day is the day of
     * the team rules only; see the class comment.
     */
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    /**
     * IANA name of the zone of every scheduled job's cron. The jobs follow UTC
     * data, not the Istanbul business day: sprint series dates roll over at
     * 00:00 UTC, and session retention compares instants only. UTC also keeps
     * each run at the same hour on every host and across daylight saving time.
     * An annotation attribute takes only a compile-time constant, so this holds
     * the name, not a ZoneId.
     */
    public static final String SCHEDULER_ZONE_ID = "UTC";

    private final Clock clock;

    /**
     * Returns the business day now.
     *
     * @return the current day in {@link #ZONE}
     */
    public LocalDate today() {
        return dayOf(clock.instant());
    }

    /**
     * Returns the business day an instant falls on.
     *
     * @param instant the moment
     * @return the day of the moment in {@link #ZONE}
     */
    public LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    /**
     * Returns the instant at which a business day starts.
     *
     * @param day the business day
     * @return 00:00 of the day in {@link #ZONE}
     */
    public Instant startOfDay(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    /**
     * Returns the instants that bound a range of business days: from the start
     * of the first day, inclusive, to the start of the day after the last day,
     * exclusive. A missing side leaves that bound open.
     *
     * @param from first day, inclusive; null for no lower bound
     * @param to   last day, inclusive; null for no upper bound
     * @return the bounds; a side is null when its day is null
     */
    public DayRange dayRange(LocalDate from, LocalDate to) {
        Instant start = from == null ? null : startOfDay(from);
        Instant end = to == null ? null : startOfDay(to.plusDays(1));
        return new DayRange(start, end);
    }

    /**
     * Bounds of a range of business days.
     *
     * @param start start of the first day, inclusive; null for no lower bound
     * @param end   start of the day after the last day, exclusive; null for no upper bound
     */
    public record DayRange(Instant start, Instant end) {
    }
}
