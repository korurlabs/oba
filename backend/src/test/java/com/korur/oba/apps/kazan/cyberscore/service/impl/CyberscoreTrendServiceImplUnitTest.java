package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreTrendPoint;
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreSnapshot;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreSnapshotRepository;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;

@ExtendWith(MockitoExtension.class)
class CyberscoreTrendServiceImplUnitTest {

    // 13:00 in Istanbul on 2026-11-25.
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private CyberscoreRepository cyberscoreRepository;

    @Mock
    private CyberscoreSnapshotRepository snapshotRepository;

    @Mock
    private LoggingService loggingService;

    private CyberscoreTrendServiceImpl service;

    @BeforeEach
    void setUp() {
        BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC));
        service = new CyberscoreTrendServiceImpl(cyberscoreRepository, snapshotRepository, calendar, loggingService);
        lenient().when(snapshotRepository.findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(any(), any()))
                .thenReturn(List.of());
        lenient().when(snapshotRepository.findFirstByOrganizationIdAndTakenAtBeforeOrderByTakenAtDesc(any(), any()))
                .thenReturn(Optional.empty());
    }

    private static Cyberscore scoredRow(int percentage, CyberscoreLevel level) {
        return Cyberscore.builder().scorePercentage(percentage).scoreLevel(level)
                .lastCalculatedAt(NOW).build();
    }

    private static CyberscoreSnapshot snapshot(String takenAt, int percentage, CyberscoreLevel level) {
        return CyberscoreSnapshot.builder().takenAt(Instant.parse(takenAt)).scorePercentage(percentage)
                .scoreLevel(level).build();
    }

    @Test
    void buildTrend_noRow_returnsEmpty() {
        when(cyberscoreRepository.findByOrganizationId(organizationId)).thenReturn(Optional.empty());

        assertThat(service.buildTrend(organizationId, 30)).isEmpty();
    }

    @Test
    void buildTrend_rowNeverCalculated_returnsEmpty() {
        when(cyberscoreRepository.findByOrganizationId(organizationId)).thenReturn(Optional.of(Cyberscore.builder().build()));

        assertThat(service.buildTrend(organizationId, 30)).isEmpty();
    }

    @Test
    void buildTrend_rowUnknown_returnsEmpty() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(0, CyberscoreLevel.UNKNOWN)));

        assertThat(service.buildTrend(organizationId, 30)).isEmpty();
    }

    @Test
    void buildTrend_noSnapshots_returnsTodayFromTheRow() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(62, CyberscoreLevel.LOW)));

        assertThat(service.buildTrend(organizationId, 30))
                .containsExactly(new CyberscoreTrendPoint(TODAY, 62, CyberscoreLevel.LOW));
    }

    @Test
    void buildTrend_scoreBeforeTheWindow_isCarriedIntoItsFirstDay() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(80, CyberscoreLevel.LOW)));
        when(snapshotRepository.findFirstByOrganizationIdAndTakenAtBeforeOrderByTakenAtDesc(any(), any()))
                .thenReturn(Optional.of(snapshot("2026-10-01T08:00:00Z", 40, CyberscoreLevel.HIGH)));

        List<CyberscoreTrendPoint> trend = service.buildTrend(organizationId, 7);

        assertThat(trend).containsExactly(
                new CyberscoreTrendPoint(LocalDate.of(2026, 11, 19), 40, CyberscoreLevel.HIGH),
                new CyberscoreTrendPoint(TODAY, 80, CyberscoreLevel.LOW));
    }

    @Test
    void buildTrend_carriedScoreWithUnknownBand_isLeftOut() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(80, CyberscoreLevel.LOW)));
        when(snapshotRepository.findFirstByOrganizationIdAndTakenAtBeforeOrderByTakenAtDesc(any(), any()))
                .thenReturn(Optional.of(snapshot("2026-10-01T08:00:00Z", 0, CyberscoreLevel.UNKNOWN)));

        assertThat(service.buildTrend(organizationId, 7)).extracting(CyberscoreTrendPoint::date)
                .containsExactly(TODAY);
    }

    @Test
    void buildTrend_severalSnapshotsOnOneDay_lastOneWins() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(90, CyberscoreLevel.MINIMAL)));
        when(snapshotRepository.findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(any(), any()))
                .thenReturn(List.of(
                        snapshot("2026-11-23T06:00:00Z", 10, CyberscoreLevel.CRITICAL),
                        snapshot("2026-11-23T15:00:00Z", 55, CyberscoreLevel.MEDIUM)));

        List<CyberscoreTrendPoint> trend = service.buildTrend(organizationId, 7);

        assertThat(trend).containsExactly(
                new CyberscoreTrendPoint(LocalDate.of(2026, 11, 23), 55, CyberscoreLevel.MEDIUM),
                new CyberscoreTrendPoint(TODAY, 90, CyberscoreLevel.MINIMAL));
    }

    @Test
    void buildTrend_snapshotAfterMidnightInIstanbul_countsForTheNextDay() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(90, CyberscoreLevel.MINIMAL)));
        // 21:30 UTC on the 23rd is 00:30 on the 24th in Istanbul.
        when(snapshotRepository.findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(any(), any()))
                .thenReturn(List.of(snapshot("2026-11-23T21:30:00Z", 30, CyberscoreLevel.HIGH)));

        assertThat(service.buildTrend(organizationId, 7)).extracting(CyberscoreTrendPoint::date)
                .containsExactly(LocalDate.of(2026, 11, 24), TODAY);
    }

    @Test
    void buildTrend_snapshotWithUnknownBand_isSkipped() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(90, CyberscoreLevel.MINIMAL)));
        when(snapshotRepository.findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(any(), any()))
                .thenReturn(List.of(snapshot("2026-11-23T10:00:00Z", 0, CyberscoreLevel.UNKNOWN)));

        assertThat(service.buildTrend(organizationId, 7)).extracting(CyberscoreTrendPoint::date)
                .containsExactly(TODAY);
    }

    @Test
    void buildTrend_snapshotOfTodayWithDifferentScore_isReplacedByTheRow() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(90, CyberscoreLevel.MINIMAL)));
        when(snapshotRepository.findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(any(), any()))
                .thenReturn(List.of(snapshot("2026-11-25T08:00:00Z", 70, CyberscoreLevel.LOW)));

        assertThat(service.buildTrend(organizationId, 7))
                .containsExactly(new CyberscoreTrendPoint(TODAY, 90, CyberscoreLevel.MINIMAL));
    }

    @Test
    void buildTrend_daysBelowOneOrAbove365_areClamped() {
        when(cyberscoreRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(scoredRow(90, CyberscoreLevel.MINIMAL)));
        Instant startOfToday = Instant.parse("2026-11-24T21:00:00Z");
        Instant startOf365 = TODAY.minusDays(364).atStartOfDay(BusinessCalendar.ZONE).toInstant();

        service.buildTrend(organizationId, -5);
        service.buildTrend(organizationId, 0);
        service.buildTrend(organizationId, 10_000);

        verify(snapshotRepository, org.mockito.Mockito.times(2))
                .findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(organizationId, startOfToday);
        verify(snapshotRepository).findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(organizationId, startOf365);
    }
}
