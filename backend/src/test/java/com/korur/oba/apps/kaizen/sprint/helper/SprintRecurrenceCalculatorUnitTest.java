package com.korur.oba.apps.kaizen.sprint.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.atEndSeries;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.series;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.sprint;
import static com.korur.oba.apps.kaizen.sprint.mock_data.SprintRecurrenceMocks.utc;

// 3. Java SDK
import java.time.Instant;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kaizen.sprint.enums.SprintRecurrenceTicketAction;
import com.korur.oba.apps.kaizen.sprint.enums.SprintStatus;

class SprintRecurrenceCalculatorUnitTest {

    private final SprintRecurrenceCalculator calculator = new SprintRecurrenceCalculator();

    private static SprintSeries daysBefore(Integer days) {
        return series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                SprintRecurrenceCreationMode.DAYS_BEFORE, days, 7);
    }

    @Nested
    class LengthDays {

        @Test
        void bothDates_returnsWholeDays() {
            Sprint sprint = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-15"));

            assertEquals(14, calculator.lengthDays(sprint));
        }

        @Test
        void missingStartDate_returnsNull() {
            Sprint sprint = sprint(SprintStatus.PLANNED, null, utc("2026-01-15"));

            assertNull(calculator.lengthDays(sprint));
        }

        @Test
        void missingEndDate_returnsNull() {
            Sprint sprint = sprint(SprintStatus.PLANNED, utc("2026-01-01"), null);

            assertNull(calculator.lengthDays(sprint));
        }
    }

    @Nested
    class CurrentSprint {

        @Test
        void activeBelowPlannedHead_returnsTheActiveSprint() {
            Sprint ahead = sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15"));
            Sprint active = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));

            assertSame(active, calculator.currentSprint(List.of(ahead, active)).orElseThrow());
        }

        @Test
        void noActiveSprint_returnsThePlannedHead() {
            Sprint planned = sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15"));
            Sprint done = sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-08"));

            assertSame(planned, calculator.currentSprint(List.of(planned, done)).orElseThrow());
        }

        @Test
        void onlyCompletedSprints_returnsEmpty() {
            Sprint done = sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-08"));

            assertTrue(calculator.currentSprint(List.of(done)).isEmpty());
        }
    }

    @Nested
    class ClosingEndsSeries {

        @Test
        void onlyLiveSprintIsTheOneClosing_returnsTrue() {
            Sprint active = sprint(SprintStatus.ACTIVE, utc("2026-01-08"), utc("2026-01-15"));
            Sprint done = sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-08"));

            assertTrue(calculator.closingEndsSeries(active, List.of(active, done)));
        }

        @Test
        void nextSprintIsPlannedAhead_returnsFalse() {
            Sprint ahead = sprint(SprintStatus.PLANNED, utc("2026-01-15"), utc("2026-01-22"));
            Sprint active = sprint(SprintStatus.ACTIVE, utc("2026-01-08"), utc("2026-01-15"));

            assertFalse(calculator.closingEndsSeries(active, List.of(ahead, active)));
        }

        @Test
        void anotherSprintOfTheSeriesRuns_returnsFalse() {
            Sprint closing = sprint(SprintStatus.ACTIVE, utc("2026-01-08"), utc("2026-01-15"));
            Sprint other = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-15"));

            assertFalse(calculator.closingEndsSeries(closing, List.of(closing, other)));
        }

        @Test
        void sprintMissingFromTheList_returnsTrueWhenNothingElseRuns() {
            Sprint closing = sprint(SprintStatus.ACTIVE, utc("2026-01-08"), utc("2026-01-15"));
            Sprint done = sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-08"));

            assertTrue(calculator.closingEndsSeries(closing, List.of(done)));
            assertTrue(calculator.closingEndsSeries(closing, List.of()));
        }
    }

    @Nested
    class NextRolloverAt {

        @Test
        void enabledWithActiveSprint_returnsEndDate() {
            Sprint sprint = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));

            assertEquals(utc("2026-01-08"), calculator.nextRolloverAt(atEndSeries(), List.of(sprint)));
        }

        @Test
        void enabledWithOnlyPlannedSprint_returnsStartDate() {
            Sprint sprint = sprint(SprintStatus.PLANNED, utc("2026-01-01"), utc("2026-01-08"));

            assertEquals(utc("2026-01-01"), calculator.nextRolloverAt(atEndSeries(), List.of(sprint)));
        }

        @Test
        void disabled_returnsNull() {
            Sprint sprint = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));
            SprintSeries series = atEndSeries();
            series.setEnabled(false);

            assertNull(calculator.nextRolloverAt(series, List.of(sprint)));
        }

        @Test
        void onlyCompletedSprints_returnsNull() {
            Sprint sprint = sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-08"));

            assertNull(calculator.nextRolloverAt(atEndSeries(), List.of(sprint)));
        }
    }

    @Nested
    class NextCreationAt {

        private final Sprint active = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));

        @Test
        void daysBefore_returnsEndDateMinusDays() {
            assertEquals(utc("2026-01-06"), calculator.nextCreationAt(daysBefore(2), List.of(active)));
        }

        @Test
        void onlyPlannedSprint_countsFromItsEndDate() {
            Sprint planned = sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15"));

            assertEquals(utc("2026-01-13"), calculator.nextCreationAt(daysBefore(2), List.of(planned)));
        }

        @Test
        void atEndMode_returnsNull() {
            assertNull(calculator.nextCreationAt(atEndSeries(), List.of(active)));
        }

        @Test
        void disabled_returnsNull() {
            SprintSeries series = daysBefore(2);
            series.setEnabled(false);

            assertNull(calculator.nextCreationAt(series, List.of(active)));
        }

        @Test
        void sprintAlreadyCreatedAhead_returnsNull() {
            Sprint ahead = sprint(SprintStatus.PLANNED, utc("2026-01-08"), utc("2026-01-15"));

            assertNull(calculator.nextCreationAt(daysBefore(2), List.of(ahead, active)));
        }

        @Test
        void onlyCompletedSprints_returnsNull() {
            Sprint done = sprint(SprintStatus.COMPLETED, utc("2026-01-01"), utc("2026-01-08"));

            assertNull(calculator.nextCreationAt(daysBefore(2), List.of(done)));
        }

        @Test
        void missingEndDate_returnsNull() {
            Sprint planned = sprint(SprintStatus.PLANNED, utc("2026-01-01"), null);

            assertNull(calculator.nextCreationAt(daysBefore(2), List.of(planned)));
        }

        @Test
        void missingDays_returnsNull() {
            assertNull(calculator.nextCreationAt(daysBefore(null), List.of(active)));
        }

        @Test
        void daysEqualToSprintLength_returnsNull() {
            assertNull(calculator.nextCreationAt(daysBefore(7), List.of(active)));
        }

        @Test
        void daysOneBelowSprintLength_returnsStartPlusOneDay() {
            assertEquals(utc("2026-01-02"), calculator.nextCreationAt(daysBefore(6), List.of(active)));
        }
    }

    @Nested
    class DaysBeforeFitsLength {

        private final Sprint sprint = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));

        @Test
        void atEndMode_returnsTrue() {
            assertTrue(calculator.daysBeforeFitsLength(sprint, atEndSeries()));
        }

        @Test
        void daysBelowSprintLength_returnsTrue() {
            assertTrue(calculator.daysBeforeFitsLength(sprint, daysBefore(6)));
        }

        @Test
        void daysNotBelowSprintLength_returnsFalse() {
            assertFalse(calculator.daysBeforeFitsLength(sprint, daysBefore(7)));
        }
    }

    @Nested
    class RolloverAt {

        private final Sprint closing = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));
        private final SprintSeries series = atEndSeries();

        @Test
        void nowBeforeEndDate_returnsEndDate() {
            assertEquals(utc("2026-01-08"), calculator.rolloverAt(closing, series, utc("2026-01-05")));
        }

        @Test
        void nowEqualsEndDate_returnsEndDate() {
            assertEquals(utc("2026-01-08"), calculator.rolloverAt(closing, series, utc("2026-01-08")));
        }

        @Test
        void midnightHourRetry_returnsEndDate() {
            Instant retry = Instant.parse("2026-01-08T00:45:00Z");

            assertEquals(utc("2026-01-08"), calculator.rolloverAt(closing, series, retry));
        }

        @Test
        void downOverThreePeriods_returnsStartOfPeriodContainingNow() {
            // E = Jan 8, L = 7: periods start Jan 15, Jan 22, Jan 29. Now is inside the third.
            Instant now = Instant.parse("2026-01-30T10:00:00Z");

            assertEquals(utc("2026-01-29"), calculator.rolloverAt(closing, series, now));
        }

        @Test
        void nowOnPeriodBoundary_returnsThatBoundary() {
            assertEquals(utc("2026-01-22"), calculator.rolloverAt(closing, series, utc("2026-01-22")));
        }

        @Test
        void nowJustBeforeThirdPeriod_returnsSecondBoundary() {
            Instant now = Instant.parse("2026-01-28T23:59:59Z");

            assertEquals(utc("2026-01-22"), calculator.rolloverAt(closing, series, now));
        }

        @Test
        void periodsCountInSeriesLengthNotSprintLength() {
            // The closing sprint ran 7 days, the series now makes 14-day sprints.
            SprintSeries longer = series(SprintRecurrenceTicketAction.MOVE_TO_NEXT_SPRINT,
                    SprintRecurrenceCreationMode.AT_END, null, 14);

            assertEquals(utc("2026-01-22"), calculator.rolloverAt(closing, longer, utc("2026-01-30")));
        }
    }

    @Nested
    class NextEndDate {

        private final Sprint closing = sprint(SprintStatus.ACTIVE, utc("2026-01-01"), utc("2026-01-08"));
        private final SprintSeries series = atEndSeries();

        @Test
        void onTimeWithPlannedEnd_keepsPlannedEnd() {
            assertEquals(utc("2026-01-20"),
                    calculator.nextEndDate(closing, series, utc("2026-01-08"), utc("2026-01-20")));
        }

        @Test
        void onTimeWithoutPlannedSprint_returnsStartPlusSeriesLength() {
            assertEquals(utc("2026-01-15"), calculator.nextEndDate(closing, series, utc("2026-01-08"), null));
        }

        @Test
        void onTimePlannedEndNotAfterStart_returnsStartPlusSeriesLength() {
            assertEquals(utc("2026-01-15"),
                    calculator.nextEndDate(closing, series, utc("2026-01-08"), utc("2026-01-08")));
        }

        @Test
        void afterCatchUp_resetsToStartPlusSeriesLength() {
            assertEquals(utc("2026-02-05"),
                    calculator.nextEndDate(closing, series, utc("2026-01-29"), utc("2026-01-20")));
        }
    }
}
