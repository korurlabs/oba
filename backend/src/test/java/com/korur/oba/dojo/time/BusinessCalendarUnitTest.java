package com.korur.oba.dojo.time;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

// 4. Internal
import com.korur.oba.dojo.time.BusinessCalendar.DayRange;

class BusinessCalendarUnitTest {

    // Istanbul is UTC+3 all year, so 21:30Z is 00:30 of the next Istanbul day.
    private static final Instant EVENING_UTC = Instant.parse("2026-11-03T21:30:00Z");

    private static BusinessCalendar calendarAt(Instant now) {
        return new BusinessCalendar(Clock.fixed(now, ZoneOffset.UTC));
    }

    @Nested
    class Today {

        @Test
        void at2130Utc_returnsNextIstanbulDay() {
            assertThat(calendarAt(EVENING_UTC).today()).isEqualTo(LocalDate.of(2026, 11, 4));
        }

        @Test
        void at2059Utc_returnsSameIstanbulDay() {
            assertThat(calendarAt(Instant.parse("2026-11-03T20:59:59Z")).today())
                    .isEqualTo(LocalDate.of(2026, 11, 3));
        }
    }

    @Nested
    class DayOf {

        @Test
        void at2130Utc_returnsNextIstanbulDay() {
            assertThat(calendarAt(EVENING_UTC).dayOf(EVENING_UTC)).isEqualTo(LocalDate.of(2026, 11, 4));
        }

        @Test
        void instantIgnoresClock_usesGivenInstant() {
            assertThat(calendarAt(EVENING_UTC).dayOf(Instant.parse("2026-11-03T10:00:00Z")))
                    .isEqualTo(LocalDate.of(2026, 11, 3));
        }
    }

    @Nested
    class StartOfDay {

        @Test
        void istanbulDay_startsAt2100UtcThePreviousDay() {
            assertThat(calendarAt(EVENING_UTC).startOfDay(LocalDate.of(2026, 11, 4)))
                    .isEqualTo(Instant.parse("2026-11-03T21:00:00Z"));
        }
    }

    @Nested
    class DayRangeOf {

        @Test
        void bothDays_startInclusiveAndEndAtStartOfNextDay() {
            DayRange range = calendarAt(EVENING_UTC).dayRange(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5));

            assertThat(range.start()).isEqualTo(Instant.parse("2026-11-03T21:00:00Z"));
            assertThat(range.end()).isEqualTo(Instant.parse("2026-11-05T21:00:00Z"));
        }

        @Test
        void sameDay_coversOneIstanbulDay() {
            DayRange range = calendarAt(EVENING_UTC).dayRange(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4));

            assertThat(range.start()).isEqualTo(Instant.parse("2026-11-03T21:00:00Z"));
            assertThat(range.end()).isEqualTo(Instant.parse("2026-11-04T21:00:00Z"));
        }

        @Test
        void nullFrom_startIsNull() {
            DayRange range = calendarAt(EVENING_UTC).dayRange(null, LocalDate.of(2026, 11, 4));

            assertThat(range.start()).isNull();
            assertThat(range.end()).isEqualTo(Instant.parse("2026-11-04T21:00:00Z"));
        }

        @Test
        void nullTo_endIsNull() {
            DayRange range = calendarAt(EVENING_UTC).dayRange(LocalDate.of(2026, 11, 4), null);

            assertThat(range.start()).isEqualTo(Instant.parse("2026-11-03T21:00:00Z"));
            assertThat(range.end()).isNull();
        }

        @Test
        void bothNull_bothSidesNull() {
            DayRange range = calendarAt(EVENING_UTC).dayRange(null, null);

            assertThat(range.start()).isNull();
            assertThat(range.end()).isNull();
        }
    }
}
