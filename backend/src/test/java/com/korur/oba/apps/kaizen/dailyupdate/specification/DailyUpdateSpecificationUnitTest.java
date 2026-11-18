package com.korur.oba.apps.kaizen.dailyupdate.specification;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 2. Third-party
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.dto.request.DailyUpdateQueryRequest;
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdate;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.dojo.time.BusinessCalendar.DayRange;

/**
 * Checks the bounds the feed's date filter hands to the Criteria API, against a mocked
 * CriteriaBuilder: no PostgreSQL runs here.
 */
class DailyUpdateSpecificationUnitTest {

    private final BusinessCalendar businessCalendar =
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC));

    private Root<DailyUpdate> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class, RETURNS_DEEP_STUBS);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
    }

    @Test
    void filterBy_updateAt2230UtcOn3Nov_belongsToIstanbulReportDay4Nov() {
        DailyUpdateQueryRequest request = new DailyUpdateQueryRequest();
        LocalDate fourthNov = LocalDate.of(2026, 11, 4);
        DayRange range = businessCalendar.dayRange(fourthNov, fourthNov);

        DailyUpdateSpecification.filterBy(List.of(UUID.randomUUID()), request, range)
                .toPredicate(root, query, cb);

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(cb, atLeastOnce()).greaterThanOrEqualTo(any(Expression.class), start.capture());
        verify(cb, atLeastOnce()).lessThan(any(Expression.class), end.capture());
        Instant update = Instant.parse("2026-11-03T22:30:00Z");
        assertThat(update).isAfterOrEqualTo(start.getValue()).isBefore(end.getValue());
        assertThat(Instant.parse("2026-11-03T20:59:59Z")).isBefore(start.getValue());
        assertThat(Instant.parse("2026-11-04T21:00:00Z")).isAfterOrEqualTo(end.getValue());
    }

    @Test
    void filterBy_nullRange_addsNoCreatedAtBound() {
        DailyUpdateSpecification.filterBy(List.of(UUID.randomUUID()), new DailyUpdateQueryRequest(), businessCalendar.dayRange(null, null))
                .toPredicate(root, query, cb);

        verify(cb, org.mockito.Mockito.never()).lessThan(any(Expression.class), any(Instant.class));
        verify(cb, org.mockito.Mockito.never()).greaterThanOrEqualTo(any(Expression.class), any(Instant.class));
    }
}
