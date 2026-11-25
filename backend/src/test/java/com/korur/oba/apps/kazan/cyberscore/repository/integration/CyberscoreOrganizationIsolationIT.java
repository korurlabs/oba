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
import static org.junit.jupiter.api.Assertions.assertTrue;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreSnapshot;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckStatus;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreSnapshotEventType;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreCheckRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreSnapshotRepository;
import com.korur.oba.support.ObaIntegrationTest;

/**
 * Runs the cyberscore queries with two organizations in one database, because what they protect
 * is the isolation of the score data: an organization lists, finds, counts and trends only its own
 * checks, score row and snapshots, even when both use the same check name, and the score row of one
 * organization is a separate row from the other's.
 *
 * Each test creates its own organizations and removes them in the cleanup (the rows go with them).
 */
class CyberscoreOrganizationIsolationIT extends ObaIntegrationTest {

    private static final String NAME = "Multi-factor sign-in";

    private final UUID orgA = UUID.randomUUID();
    private final UUID orgB = UUID.randomUUID();

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
    void insertOrganizations() {
        for (UUID id : List.of(orgA, orgB)) {
            jdbc.update("INSERT INTO shrine.organizations (id, name, status) VALUES (?, ?, 'ACTIVE')",
                    id, "itest org " + id);
        }
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM shrine.organizations WHERE id IN (?, ?)", orgA, orgB);
    }

    private CyberscoreCheck saveCheck(UUID organizationId, String name, CyberscoreCheckStatus status) {
        return checkRepository.saveAndFlush(CyberscoreCheck.builder().organizationId(organizationId).name(name)
                .expected("expected").weight(5).category(CyberscoreCheckCategory.DEVICES).status(status).build());
    }

    @Test
    void checks_sameNameInBothOrganizations_areListedAndFoundOnlyInTheirOwn() {
        CyberscoreCheck checkA = saveCheck(orgA, NAME, CyberscoreCheckStatus.IN_PROGRESS);
        CyberscoreCheck checkB = saveCheck(orgB, NAME, CyberscoreCheckStatus.IN_PROGRESS);

        assertEquals(List.of(checkA.getId()), checkRepository
                .findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(orgA).stream().map(CyberscoreCheck::getId).toList());
        assertEquals(List.of(checkB.getId()), checkRepository
                .findByOrganizationIdAndStatusOrderByCategoryAscSortOrderAscNameAsc(orgB, CyberscoreCheckStatus.IN_PROGRESS)
                .stream().map(CyberscoreCheck::getId).toList());
        assertTrue(checkRepository.findByIdAndOrganizationId(checkA.getId(), orgA).isPresent());
        assertTrue(checkRepository.findByIdAndOrganizationId(checkA.getId(), orgB).isEmpty());
    }

    @Test
    void existsByName_nameOnlyInOrganizationA_isFalseForB() {
        CyberscoreCheck checkA = saveCheck(orgA, "only a", CyberscoreCheckStatus.NOT_STARTED);

        assertTrue(checkRepository.existsByOrganizationIdAndName(orgA, "only a"));
        assertFalse(checkRepository.existsByOrganizationIdAndName(orgB, "only a"));
        assertTrue(checkRepository.existsByOrganizationIdAndNameAndIdNot(orgA, "only a", UUID.randomUUID()));
        assertFalse(checkRepository.existsByOrganizationIdAndNameAndIdNot(orgB, "only a", checkA.getId()));
    }

    @Test
    void cyberscoreRow_eachOrganization_getsItsOwnRowAndScore() {
        new TransactionTemplate(transactionManager).execute(status -> {
            cyberscoreRepository.insertIfAbsent(orgA);
            cyberscoreRepository.insertIfAbsent(orgB);
            return null;
        });
        Cyberscore rowA = cyberscoreRepository.findByOrganizationId(orgA).orElseThrow();
        rowA.setScorePercentage(90);
        rowA.setScoreLevel(CyberscoreLevel.MINIMAL);
        cyberscoreRepository.saveAndFlush(rowA);

        Cyberscore rowB = cyberscoreRepository.findByOrganizationId(orgB).orElseThrow();

        assertEquals(orgB, rowB.getOrganizationId());
        assertEquals(0, rowB.getScorePercentage());
        assertEquals(CyberscoreLevel.UNKNOWN, rowB.getScoreLevel());
        assertFalse(rowA.getId().equals(rowB.getId()));
    }

    @Test
    void lockedRow_ofOrganizationA_isNeverTheRowOfB() {
        new TransactionTemplate(transactionManager).execute(status -> cyberscoreRepository.insertIfAbsent(orgA));

        assertTrue(new TransactionTemplate(transactionManager)
                .execute(status -> cyberscoreRepository.findByOrganizationIdForUpdate(orgB)).isEmpty());
    }

    @Test
    void snapshots_sameWindowInBothOrganizations_areReadPerOrganization() {
        Instant takenAt = Instant.parse("2026-11-10T10:00:00Z");
        UUID snapshotA = saveSnapshot(orgA, takenAt, 20);
        UUID snapshotB = saveSnapshot(orgB, takenAt, 70);
        Instant from = takenAt.minusSeconds(60);
        Instant before = takenAt.plusSeconds(60);

        assertEquals(List.of(snapshotA), snapshotRepository
                .findByOrganizationIdAndTakenAtGreaterThanEqualOrderByTakenAtAsc(orgA, from).stream()
                .map(CyberscoreSnapshot::getId).toList());
        assertEquals(snapshotB, snapshotRepository
                .findFirstByOrganizationIdAndTakenAtBeforeOrderByTakenAtDesc(orgB, before).orElseThrow().getId());
        assertEquals(70, snapshotRepository
                .findFirstByOrganizationIdAndTakenAtBeforeOrderByTakenAtDesc(orgB, before).orElseThrow()
                .getScorePercentage());
    }

    @Test
    void organizationDeleted_removesItsChecksScoreAndSnapshotsOnly() {
        saveCheck(orgA, NAME, CyberscoreCheckStatus.NOT_STARTED);
        saveCheck(orgB, NAME, CyberscoreCheckStatus.NOT_STARTED);
        new TransactionTemplate(transactionManager).execute(status -> cyberscoreRepository.insertIfAbsent(orgA));
        new TransactionTemplate(transactionManager).execute(status -> cyberscoreRepository.insertIfAbsent(orgB));
        saveSnapshot(orgA, Instant.parse("2026-11-10T10:00:00Z"), 20);
        saveSnapshot(orgB, Instant.parse("2026-11-10T10:00:00Z"), 20);

        jdbc.update("DELETE FROM shrine.organizations WHERE id = ?", orgA);

        assertEquals(0, count("SELECT count(*) FROM kazan.cyberscore_checks WHERE organization_id = ?", orgA));
        assertEquals(0, count("SELECT count(*) FROM kazan.cyberscores WHERE organization_id = ?", orgA));
        assertEquals(0, count("SELECT count(*) FROM kazan.cyberscore_snapshots WHERE organization_id = ?", orgA));
        assertEquals(1, count("SELECT count(*) FROM kazan.cyberscore_checks WHERE organization_id = ?", orgB));
        assertEquals(1, count("SELECT count(*) FROM kazan.cyberscores WHERE organization_id = ?", orgB));
        assertEquals(1, count("SELECT count(*) FROM kazan.cyberscore_snapshots WHERE organization_id = ?", orgB));
    }

    private UUID saveSnapshot(UUID organizationId, Instant takenAt, int percentage) {
        return snapshotRepository.saveAndFlush(CyberscoreSnapshot.builder().organizationId(organizationId)
                .scorePercentage(percentage).scoreLevel(CyberscoreLevel.fromPercentage(percentage))
                .eventType(CyberscoreSnapshotEventType.CHECK_CREATED).takenAt(takenAt).build()).getId();
    }
}
