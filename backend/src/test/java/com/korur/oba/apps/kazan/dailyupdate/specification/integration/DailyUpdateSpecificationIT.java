package com.korur.oba.apps.kazan.dailyupdate.specification.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 2. Third-party
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 3. Java SDK
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.dto.request.DailyUpdateQueryRequest;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdate;
import com.korur.oba.apps.kazan.dailyupdate.repository.DailyUpdateRepository;
import com.korur.oba.apps.kazan.dailyupdate.specification.DailyUpdateSpecification;
import com.korur.oba.dojo.time.BusinessCalendar;
import com.korur.oba.dojo.time.BusinessCalendar.DayRange;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * The report-day range of DailyUpdateSpecification against real SQL: the start of the first day
 * is inclusive and the start of the day after the last day is exclusive, on the Istanbul day.
 * Rows get exact created_at values under one test author, and every query filters on that
 * author, so a database shared with a running backend is not disturbed.
 */
class DailyUpdateSpecificationIT extends ObaIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 4);
    private static final Instant START_OF_DAY = Instant.parse("2026-11-03T21:00:00Z");
    private static final Instant END_OF_DAY = Instant.parse("2026-11-04T21:00:00Z");
    private static final Instant JUST_BEFORE_END = Instant.parse("2026-11-04T20:59:59.999Z");
    private static final Instant JUST_BEFORE_START = Instant.parse("2026-11-03T20:59:59.999Z");

    @Autowired
    private DailyUpdateRepository dailyUpdateRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private final BusinessCalendar businessCalendar =
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC));

    private UUID authorId;

    @BeforeEach
    void setUp() {
        authorId = UUID.randomUUID();
        jdbc.update("INSERT INTO shrine.users (id, email) VALUES (?, ?)",
                authorId, "itest-" + authorId + "@example.invalid");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM kazan.daily_updates WHERE author_id = ?", authorId);
        jdbc.update("DELETE FROM shrine.users WHERE id = ?", authorId);
    }

    @Test
    @DisplayName("filterBy includes a row created exactly at the start of the Istanbul day")
    void filterBy_rowAtStartOfDay_isIncluded() {
        insertRow(START_OF_DAY);

        assertEquals(1, find(businessCalendar.dayRange(DAY, DAY)).size());
    }

    @Test
    @DisplayName("filterBy excludes a row one millisecond before the start of the day")
    void filterBy_rowJustBeforeStartOfDay_isExcluded() {
        insertRow(JUST_BEFORE_START);

        assertEquals(0, find(businessCalendar.dayRange(DAY, DAY)).size());
    }

    @Test
    @DisplayName("filterBy includes a row at 20:59:59.999Z, the last millisecond of the day")
    void filterBy_rowJustBeforeEndOfDay_isIncluded() {
        insertRow(JUST_BEFORE_END);

        assertEquals(1, find(businessCalendar.dayRange(DAY, DAY)).size());
    }

    @Test
    @DisplayName("filterBy excludes a row exactly at the exclusive end, the start of the next day")
    void filterBy_rowAtEndOfDay_isExcluded() {
        insertRow(END_OF_DAY);

        assertEquals(0, find(businessCalendar.dayRange(DAY, DAY)).size());
    }

    @Test
    @DisplayName("filterBy with only an end day excludes the row at the end and keeps the earlier row")
    void filterBy_toOnly_excludesRowAtEnd() {
        insertRow(END_OF_DAY);
        insertRow(JUST_BEFORE_END);

        List<DailyUpdate> rows = find(businessCalendar.dayRange(null, DAY));

        assertEquals(1, rows.size());
        assertEquals(JUST_BEFORE_END, rows.get(0).getCreatedAt());
    }

    @Test
    @DisplayName("filterBy with only a start day keeps the row at the start and the later row")
    void filterBy_fromOnly_keepsRowAtStartAndLater() {
        insertRow(JUST_BEFORE_START);
        insertRow(START_OF_DAY);
        insertRow(END_OF_DAY);

        assertEquals(2, find(businessCalendar.dayRange(DAY, null)).size());
    }

    private List<DailyUpdate> find(DayRange range) {
        return dailyUpdateRepository.findAll(DailyUpdateSpecification.filterBy(
                List.of(authorId), new DailyUpdateQueryRequest(), range));
    }

    private void insertRow(Instant createdAt) {
        jdbc.update("""
                INSERT INTO kazan.daily_updates (id, author_id, deleted, created_at, updated_at)
                VALUES (?, ?, false, ?, ?)
                """, UUID.randomUUID(), authorId, Timestamp.from(createdAt), Timestamp.from(createdAt));
    }
}
