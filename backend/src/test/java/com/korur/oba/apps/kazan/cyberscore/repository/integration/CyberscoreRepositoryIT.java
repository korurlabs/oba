package com.korur.oba.apps.kazan.cyberscore.repository.integration;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 2. Third-party
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreSnapshot;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckStatus;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreSnapshotEventType;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreCheckRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreSnapshotRepository;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the cyberscore queries against the Flyway-migrated PostgreSQL schema, because what they
 * protect lives in the database: the display order is the PostgreSQL enum order with the
 * uncategorized checks last, the score row is created without a unique violation when two writers
 * start together, and the PostgreSQL enum and timestamp columns round-trip unchanged. The Spring Data
 * derived queries are also parsed when the application context starts, which this class loads.
 *
 * Each test creates its own organization and removes it in the cleanup (the rows go with it).
 */
class CyberscoreRepositoryIT extends ObaIntegrationTest {

    private final UUID organizationId = UUID.randomUUID();

    @Autowired
    private CyberscoreCheckRepository checkRepository;

    @Autowired
    private CyberscoreRepository cyberscoreRepository;

    @Autowired
    private CyberscoreSnapshotRepository snapshotRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void insertOrganization() {
        jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                organizationId, "itest org " + organizationId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", organizationId);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private CyberscoreCheck saveCheck(String name, CyberscoreCheckCategory category, int sortOrder, CyberscoreCheckStatus status) {
        return checkRepository.saveAndFlush(CyberscoreCheck.builder().organizationId(organizationId).name(name)
                .expected("expected").weight(5).sortOrder(sortOrder).category(category).status(status).build());
    }

    @Test
    void insertIfAbsent_calledTwice_insertsOnceAndKeepsTheColumnDefaults() {
        Integer first = inTransaction(() -> cyberscoreRepository.insertIfAbsent(organizationId));
        Integer second = inTransaction(() -> cyberscoreRepository.insertIfAbsent(organizationId));

        assertEquals(1, first);
        assertEquals(0, second);
        Cyberscore row = cyberscoreRepository.findByOrganizationId(organizationId).orElseThrow();
        assertEquals(0, row.getScorePercentage());
        assertEquals(CyberscoreLevel.UNKNOWN, row.getScoreLevel());
        assertEquals(null, row.getLastCalculatedAt());
        assertEquals(1, count("SELECT count(*) FROM kazan.cyberscores WHERE organization_id = ?", organizationId));
    }

    @Test
    void findByOrganizationIdForUpdate_rowExists_returnsItInsideATransaction() {
        inTransaction(() -> cyberscoreRepository.insertIfAbsent(organizationId));

        Optional<Cyberscore> locked = inTransaction(() -> cyberscoreRepository.findByOrganizationIdForUpdate(organizationId));

        assertTrue(locked.isPresent());
        assertEquals(organizationId, locked.get().getOrganizationId());
    }

    @Test
    void findByOrganizationIdForUpdate_noRow_isEmpty() {
        assertTrue(inTransaction(() -> cyberscoreRepository.findByOrganizationIdForUpdate(organizationId)).isEmpty());
    }

    @Test
    void scoreRow_savedAndReloaded_keepsPercentageLevelAndCalculationTime() {
        Cyberscore saved = cyberscoreRepository.saveAndFlush(Cyberscore.builder().organizationId(organizationId)
                .scorePercentage(61).scoreLevel(CyberscoreLevel.LOW)
                .lastCalculatedAt(Instant.parse("2026-11-25T10:00:00Z")).build());

        Cyberscore reloaded = cyberscoreRepository.findByOrganizationId(organizationId).orElseThrow();

        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(61, reloaded.getScorePercentage());
        assertEquals(CyberscoreLevel.LOW, reloaded.getScoreLevel());
        assertEquals(Instant.parse("2026-11-25T10:00:00Z"), reloaded.getLastCalculatedAt());
    }

    @Test
    void listChecks_mixedCategories_sortsByEnumOrderThenSortOrderThenNameWithUncategorizedLast() {
        saveCheck("z uncategorized", null, 0, CyberscoreCheckStatus.NOT_STARTED);
        saveCheck("b compliance", CyberscoreCheckCategory.COMPLIANCE, 0, CyberscoreCheckStatus.NOT_STARTED);
        saveCheck("b identity", CyberscoreCheckCategory.IDENTITY, 1, CyberscoreCheckStatus.NOT_STARTED);
        saveCheck("c identity", CyberscoreCheckCategory.IDENTITY, 0, CyberscoreCheckStatus.NOT_STARTED);
        saveCheck("a identity", CyberscoreCheckCategory.IDENTITY, 1, CyberscoreCheckStatus.NOT_STARTED);
        saveCheck("a uncategorized", null, 0, CyberscoreCheckStatus.NOT_STARTED);

        List<String> names = checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId)
                .stream().map(CyberscoreCheck::getName).toList();

        assertEquals(List.of("c identity", "a identity", "b identity", "b compliance", "a uncategorized", "z uncategorized"),
                names);
    }

    @Test
    void listChecks_statusFilter_returnsOnlyThatStatus() {
        saveCheck("one", CyberscoreCheckCategory.DEVICES, 0, CyberscoreCheckStatus.IN_PROGRESS);
        saveCheck("two", CyberscoreCheckCategory.DEVICES, 1, CyberscoreCheckStatus.DONE);

        List<CyberscoreCheck> inProgress = checkRepository
                .findByOrganizationIdAndStatusOrderByCategoryAscSortOrderAscNameAsc(organizationId, CyberscoreCheckStatus.IN_PROGRESS);

        assertEquals(List.of("one"), inProgress.stream().map(CyberscoreCheck::getName).toList());
    }

    @Test
    void saveCheck_withoutOutcomeAndStatus_appliesTheDefaults() {
        CyberscoreCheck saved = saveCheck("defaults", null, 0, CyberscoreCheckStatus.NOT_STARTED);

        CyberscoreCheck reloaded = checkRepository.findById(saved.getId()).orElseThrow();

        assertEquals(CyberscoreCheckOutcome.TODO, reloaded.getOutcome());
        assertEquals(CyberscoreCheckStatus.NOT_STARTED, reloaded.getStatus());
        assertNotNull(reloaded.getCreatedAt());
    }

    @Test
    void existsByName_sameAndOtherCheck_distinguishesTheEditedCheck() {
        CyberscoreCheck first = saveCheck("taken", null, 0, CyberscoreCheckStatus.NOT_STARTED);
        CyberscoreCheck second = saveCheck("other", null, 0, CyberscoreCheckStatus.NOT_STARTED);

        assertTrue(checkRepository.existsByOrganizationIdAndName(organizationId, "taken"));
        assertFalse(checkRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, "taken", first.getId()));
        assertTrue(checkRepository.existsByOrganizationIdAndNameAndIdNot(organizationId, "taken", second.getId()));
    }

    @Test
    void snapshotQueries_windowAndCarryIn_returnTheExpectedRows() {
        UUID early = saveSnapshot("2026-11-01T10:00:00Z", 10);
        UUID middle = saveSnapshot("2026-11-10T10:00:00Z", 20);
        UUID late = saveSnapshot("2026-11-20T10:00:00Z", 30);
        Instant windowStart = Instant.parse("2026-11-10T10:00:00Z");

        List<UUID> inWindow = snapshotRepository
                .findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(organizationId, windowStart)
                .stream().map(CyberscoreSnapshot::getId).toList();
        Optional<CyberscoreSnapshot> carried = snapshotRepository
                .findFirstByOrganizationIdAndTakenAtBeforeOrderByTakenAtDesc(organizationId, windowStart);

        assertEquals(List.of(middle, late), inWindow);
        assertEquals(early, carried.orElseThrow().getId());
    }

    @Test
    void snapshot_saved_keepsScoreEventAndNullablePerformer() {
        UUID id = saveSnapshot("2026-11-10T10:00:00Z", 20);

        CyberscoreSnapshot reloaded = snapshotRepository.findById(id).orElseThrow();

        assertEquals(20, reloaded.getScorePercentage());
        assertEquals(CyberscoreLevel.CRITICAL, reloaded.getScoreLevel());
        assertEquals(CyberscoreSnapshotEventType.CHECK_CREATED, reloaded.getEventType());
        assertEquals(null, reloaded.getPerformedById());
        assertEquals(Instant.parse("2026-11-10T10:00:00Z"), reloaded.getTakenAt());
    }

    private UUID saveSnapshot(String takenAt, int percentage) {
        return snapshotRepository.saveAndFlush(CyberscoreSnapshot.builder().organizationId(organizationId)
                .scorePercentage(percentage).scoreLevel(CyberscoreLevel.fromPercentage(percentage))
                .eventType(CyberscoreSnapshotEventType.CHECK_CREATED).takenAt(Instant.parse(takenAt)).build()).getId();
    }
}
