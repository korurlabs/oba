package com.korur.oba.apps.kazan.milestone.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.time.LocalDate;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneHealth;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneStatus;

class MilestoneHealthResolverUnitTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private final MilestoneHealthResolver resolver = new MilestoneHealthResolver();

    private static Milestone milestone(MilestoneStatus status, LocalDate start, LocalDate target) {
        return Milestone.builder().name("M").status(status).startDate(start).targetDate(target).build();
    }

    @Test
    void closed_isDone_evenWhenPastTarget() {
        Milestone milestone = milestone(MilestoneStatus.CLOSED, null, TODAY.minusDays(30));

        assertThat(resolver.resolve(milestone, 0, 5, TODAY)).isEqualTo(MilestoneHealth.DONE);
    }

    @Test
    void openPastTarget_isOverdue() {
        Milestone milestone = milestone(MilestoneStatus.OPEN, null, TODAY.minusDays(1));

        assertThat(resolver.resolve(milestone, 5, 5, TODAY)).isEqualTo(MilestoneHealth.OVERDUE);
    }

    @Test
    void targetToday_isNotOverdue() {
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.minusDays(10), TODAY);

        assertThat(resolver.resolve(milestone, 5, 5, TODAY)).isEqualTo(MilestoneHealth.ON_TRACK);
    }

    @Test
    void moreThanFourteenDaysLeft_isOnTrack_withNoProgress() {
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.minusDays(100), TODAY.plusDays(15));

        assertThat(resolver.resolve(milestone, 0, 10, TODAY)).isEqualTo(MilestoneHealth.ON_TRACK);
    }

    @Test
    void withinWindow_progressBehindElapsedTime_isAtRisk() {
        // 20 of 30 days elapsed (0.67), 5 of 10 done (0.5).
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.minusDays(20), TODAY.plusDays(10));

        assertThat(resolver.resolve(milestone, 5, 10, TODAY)).isEqualTo(MilestoneHealth.AT_RISK);
    }

    @Test
    void withinWindow_progressAheadOfElapsedTime_isOnTrack() {
        // 20 of 30 days elapsed (0.67), 7 of 10 done (0.7).
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.minusDays(20), TODAY.plusDays(10));

        assertThat(resolver.resolve(milestone, 7, 10, TODAY)).isEqualTo(MilestoneHealth.ON_TRACK);
    }

    @Test
    void withinWindow_noTickets_countsAsNoProgress() {
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.minusDays(20), TODAY.plusDays(10));

        assertThat(resolver.resolve(milestone, 0, 0, TODAY)).isEqualTo(MilestoneHealth.AT_RISK);
    }

    @Test
    void withinWindow_startAfterToday_isOnTrack() {
        // Elapsed ratio clamps to 0, so no progress is still on track.
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.plusDays(2), TODAY.plusDays(10));

        assertThat(resolver.resolve(milestone, 0, 4, TODAY)).isEqualTo(MilestoneHealth.ON_TRACK);
    }

    @Test
    void withinWindow_noStartAndNotPersisted_countsAsStartingToday() {
        Milestone milestone = milestone(MilestoneStatus.OPEN, null, TODAY.plusDays(10));

        assertThat(resolver.resolve(milestone, 0, 4, TODAY)).isEqualTo(MilestoneHealth.ON_TRACK);
    }

    @Test
    void exactlyFourteenDaysLeft_isInsideRiskWindow() {
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.minusDays(14), TODAY.plusDays(14));

        assertThat(resolver.resolve(milestone, 0, 10, TODAY)).isEqualTo(MilestoneHealth.AT_RISK);
    }

    @Test
    void progressEqualToElapsedTime_isOnTrack() {
        // 20 of 40 days elapsed (0.5), 5 of 10 done (0.5).
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.minusDays(20), TODAY.plusDays(10).plusDays(10));

        assertThat(resolver.resolve(milestone, 5, 10, TODAY)).isEqualTo(MilestoneHealth.ON_TRACK);
    }

    @Test
    void targetNotAfterStart_countsAsFullyElapsed() {
        Milestone milestone = milestone(MilestoneStatus.OPEN, TODAY.plusDays(5), TODAY.plusDays(5));

        assertThat(resolver.resolve(milestone, 1, 2, TODAY)).isEqualTo(MilestoneHealth.AT_RISK);
    }

    @Test
    void noStartDate_usesCreationDateAsStart() {
        // Created 20 days ago, 30-day period: 0.67 elapsed, 0.5 done.
        Milestone milestone = milestone(MilestoneStatus.OPEN, null, TODAY.plusDays(10));
        milestone.setCreatedAt(TODAY.minusDays(20).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));

        assertThat(resolver.resolve(milestone, 5, 10, TODAY)).isEqualTo(MilestoneHealth.AT_RISK);
    }
}
