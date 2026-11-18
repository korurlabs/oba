package com.korur.oba.apps.kazan.dailyupdate.helper;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.helper.DailyUpdateDeadlineResolver.ReportDay;
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kazan.dailyupdate.validator.DailyUpdateValidator;
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;

@ExtendWith(MockitoExtension.class)
class DailyUpdateDeadlineResolverUnitTest {

    // Monday 2026-10-19; Istanbul is UTC+3 all year, so the deadline 10:30 there is 07:30Z.
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T06:00:00Z");

    @Mock
    private DailyUpdateRepository dailyUpdateRepository;

    private DailyUpdateDeadlineResolver resolver;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        resolver = new DailyUpdateDeadlineResolver(
                new DailyUpdateValidator(dailyUpdateRepository, new LoggingService()),
                new BusinessCalendar(clock), clock);
    }

    @Nested
    class Resolve {

        @Test
        void workday_returnsIstanbulWindowAndDeadline() {
            ReportDay day = resolver.resolve(MONDAY, Instant.parse("2026-10-19T09:00:00Z"));

            assertThat(day.date()).isEqualTo(MONDAY);
            assertThat(day.dayStart()).isEqualTo(Instant.parse("2026-10-18T21:00:00Z"));
            assertThat(day.dayEnd()).isEqualTo(Instant.parse("2026-10-19T21:00:00Z"));
            assertThat(day.deadline()).isEqualTo(Instant.parse("2026-10-19T07:30:00Z"));
            assertThat(day.workday()).isTrue();
        }

        @Test
        void nowBeforeDeadline_deadlineNotPassed() {
            assertThat(resolver.resolve(MONDAY, MONDAY_MORNING).deadlinePassed()).isFalse();
        }

        @Test
        void nowOneSecondBeforeDeadline_deadlineNotPassed() {
            assertThat(resolver.resolve(MONDAY, Instant.parse("2026-10-19T07:29:59Z")).deadlinePassed()).isFalse();
        }

        @Test
        void nowExactlyAtDeadline_deadlinePassed() {
            assertThat(resolver.resolve(MONDAY, Instant.parse("2026-10-19T07:30:00Z")).deadlinePassed()).isTrue();
        }

        @Test
        void pastDay_deadlinePassed() {
            ReportDay day = resolver.resolve(LocalDate.of(2026, 10, 16), MONDAY_MORNING);

            assertThat(day.deadlinePassed()).isTrue();
            assertThat(day.deadline()).isEqualTo(Instant.parse("2026-10-16T07:30:00Z"));
        }

        @Test
        void saturday_isNotAWorkday() {
            assertThat(resolver.resolve(LocalDate.of(2026, 10, 17), MONDAY_MORNING).workday()).isFalse();
        }

        @Test
        void sunday_isNotAWorkday() {
            assertThat(resolver.resolve(LocalDate.of(2026, 10, 18), MONDAY_MORNING).workday()).isFalse();
        }

        @Test
        void friday_isAWorkday() {
            assertThat(resolver.resolve(LocalDate.of(2026, 10, 16), MONDAY_MORNING).workday()).isTrue();
        }

        @Test
        void nullDate_resolvesToTodayInIstanbul() {
            ReportDay day = resolver.resolve(null, Instant.parse("2026-10-19T09:00:00Z"));

            assertThat(day.date()).isEqualTo(MONDAY);
            assertThat(day.deadlinePassed()).isTrue();
        }

        @Test
        void nullDateJustAfterIstanbulMidnight_resolvesToTheIstanbulDayNotTheUtcDay() {
            // 01:00 Monday in Istanbul is still Sunday 22:00 in UTC.
            ReportDay day = resolver.resolve(null, Instant.parse("2026-10-18T22:00:00Z"));

            assertThat(day.date()).isEqualTo(MONDAY);
            assertThat(day.workday()).isTrue();
            assertThat(day.deadlinePassed()).isFalse();
        }

        @Test
        void nullDateLateEveningInIstanbul_staysOnTheSameDay() {
            // 23:59 Monday in Istanbul is 20:59 in UTC.
            ReportDay day = resolver.resolve(null, Instant.parse("2026-10-19T20:59:00Z"));

            assertThat(day.date()).isEqualTo(MONDAY);
        }

        @Test
        void nullDateAtIstanbulMidnight_movesToTheNextDay() {
            ReportDay day = resolver.resolve(null, Instant.parse("2026-10-19T21:00:00Z"));

            assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 20));
        }

        @Test
        void dateEqualToIstanbulTodayButAfterUtcToday_isAccepted() {
            ReportDay day = resolver.resolve(MONDAY, Instant.parse("2026-10-18T22:00:00Z"));

            assertThat(day.date()).isEqualTo(MONDAY);
        }

        @Test
        void futureDate_throws422WithErrorCode() {
            LocalDate tomorrow = MONDAY.plusDays(1);

            assertThatThrownBy(() -> resolver.resolve(tomorrow, Instant.parse("2026-10-19T09:00:00Z")))
                    .isInstanceOfSatisfying(ValidationException.class, (ValidationException exception) ->
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ExceptionDomainKazanError.VAL_DAILY_UPDATE_REPORT_DATE_IN_FUTURE.getCode()));
        }

        @Test
        void tomorrowInIstanbulWhileStillTodayInUtc_throws422() {
            // 2026-10-19T20:00Z is Monday in UTC and Istanbul; Tuesday is a future day in both.
            // 2026-10-19T22:00Z is Tuesday 01:00 in Istanbul, so Tuesday is today there and Wednesday is future.
            Instant now = Instant.parse("2026-10-19T22:00:00Z");

            assertThat(resolver.resolve(LocalDate.of(2026, 10, 20), now).date())
                    .isEqualTo(LocalDate.of(2026, 10, 20));
            assertThatThrownBy(() -> resolver.resolve(LocalDate.of(2026, 10, 21), now))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ResolveWithClock {

        // 2026-10-19T22:30Z is 01:30 on Tuesday 20 Oct in Istanbul, a different day than in UTC.
        private final Clock fixedClock = Clock.fixed(Instant.parse("2026-10-19T22:30:00Z"), ZoneOffset.UTC);

        private DailyUpdateDeadlineResolver fixedResolver() {
            return new DailyUpdateDeadlineResolver(
                    new DailyUpdateValidator(dailyUpdateRepository, new LoggingService()),
                    new BusinessCalendar(fixedClock), fixedClock);
        }

        @Test
        void nullDate_resolvesToTheIstanbulDayOfTheClock() {
            ReportDay day = fixedResolver().resolve(null);

            assertThat(day.date()).isEqualTo(LocalDate.of(2026, 10, 20));
            assertThat(day.deadlinePassed()).isFalse();
        }

        @Test
        void istanbulTomorrow_isNotInTheFuture() {
            assertThat(fixedResolver().resolve(LocalDate.of(2026, 10, 20)).date())
                    .isEqualTo(LocalDate.of(2026, 10, 20));
        }

        @Test
        void dayAfterTheIstanbulDay_throwsValidationException() {
            assertThatThrownBy(() -> fixedResolver().resolve(LocalDate.of(2026, 10, 21)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void previousDay_deadlineHasPassedAtTheClockInstant() {
            assertThat(fixedResolver().resolve(MONDAY).deadlinePassed()).isTrue();
        }
    }
}
