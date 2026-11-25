package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

// 4. Internal
import static com.korur.oba.apps.kazan.cyberscore.constant.service.CyberscoreServiceLogConstant.*;

import com.korur.oba.apps.kazan.cyberscore.constant.CyberscoreConstant;
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreSnapshot;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreSnapshotRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreTrendPoint;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreTrendService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.time.BusinessCalendar;

/**
 * Implements the day-by-day cyberscore trend.
 *
 * Days are business days of {@link BusinessCalendar}, so a change late in the
 * evening lands on the day the team saw it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CyberscoreTrendServiceImpl implements CyberscoreTrendService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(CyberscoreTrendServiceImpl.class);

    private final CyberscoreRepository cyberscoreRepository;
    private final CyberscoreSnapshotRepository cyberscoreSnapshotRepository;
    private final BusinessCalendar businessCalendar;
    private final LoggingService loggingService;

    @Override
    public List<CyberscoreTrendPoint> buildTrend(UUID organizationId, int days) {
        Optional<Cyberscore> row = cyberscoreRepository.findByOrganizationId(organizationId);
        if (row.isEmpty() || !isScored(row.get())) {
            loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, CYBERSCORE_TREND_SKIPPED_NOT_SCORED, organizationId);
            return List.of();
        }
        Cyberscore cyberscore = row.get();

        int boundedDays = Math.clamp(days, CyberscoreConstant.MIN_TREND_DAYS, CyberscoreConstant.MAX_TREND_DAYS);
        LocalDate today = businessCalendar.today();
        LocalDate windowStart = today.minusDays(boundedDays - 1L);
        Instant windowStartInstant = businessCalendar.startOfDay(windowStart);

        // One point per day; a later put for the same day replaces the earlier
        // one, so the last snapshot of a day wins.
        TreeMap<LocalDate, CyberscoreTrendPoint> pointsByDay = new TreeMap<>();

        // The score held when the window opens, so the chart does not start empty
        // when nothing changed on its first day.
        cyberscoreSnapshotRepository
                .findFirstByOrganizationIdAndTakenAtBeforeOrderByTakenAtDesc(organizationId, windowStartInstant)
                .filter(CyberscoreTrendServiceImpl::hasScoreBand)
                .ifPresent(snapshot -> pointsByDay.put(windowStart, toPoint(windowStart, snapshot)));

        List<CyberscoreSnapshot> snapshots = cyberscoreSnapshotRepository
                .findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(organizationId, windowStartInstant);
        for (CyberscoreSnapshot snapshot : snapshots) {
            if (hasScoreBand(snapshot)) {
                LocalDate day = businessCalendar.dayOf(snapshot.getTakenAt());
                pointsByDay.put(day, toPoint(day, snapshot));
            }
        }

        // The row is the current score, also after a recalculation that wrote no snapshot.
        pointsByDay.put(today, new CyberscoreTrendPoint(today, cyberscore.getScorePercentage(),
                cyberscore.getScoreLevel()));

        List<CyberscoreTrendPoint> trend = new ArrayList<>(pointsByDay.values());
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, CYBERSCORE_TREND_BUILT,
                organizationId, windowStart, boundedDays, trend.size());
        return trend;
    }

    /**
     * A row is scored when it was calculated at least once and carries a score
     * band; otherwise there is no score to draw.
     */
    private static boolean isScored(Cyberscore cyberscore) {
        return cyberscore.getLastCalculatedAt() != null && cyberscore.getScoreLevel() != CyberscoreLevel.UNKNOWN;
    }

    /**
     * A snapshot taken while no check was scorable has the UNKNOWN band and a 0
     * score; drawing it would look like a critical score, so it is left out.
     */
    private static boolean hasScoreBand(CyberscoreSnapshot snapshot) {
        return snapshot.getScoreLevel() != CyberscoreLevel.UNKNOWN;
    }

    private static CyberscoreTrendPoint toPoint(LocalDate day, CyberscoreSnapshot snapshot) {
        return new CyberscoreTrendPoint(day, snapshot.getScorePercentage(), snapshot.getScoreLevel());
    }
}
