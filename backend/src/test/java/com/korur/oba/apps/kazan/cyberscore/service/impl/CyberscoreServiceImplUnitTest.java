package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.entity.Check;
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreSnapshot;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreSnapshotEventType;
import com.korur.oba.apps.kazan.cyberscore.repository.CheckRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreSnapshotRepository;
import com.korur.oba.dojo.exception.internal.infrastructure.InternalServerErrorException;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class CyberscoreServiceImplUnitTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final UUID organizationId = UUID.randomUUID();
    private final UUID performedById = UUID.randomUUID();

    @Mock
    private CyberscoreRepository cyberscoreRepository;

    @Mock
    private CyberscoreSnapshotRepository snapshotRepository;

    @Mock
    private CheckRepository checkRepository;

    @Mock
    private LoggingService loggingService;

    private CyberscoreServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CyberscoreServiceImpl(cyberscoreRepository, snapshotRepository, checkRepository,
                new CyberscoreCalculateServiceImpl(), Clock.fixed(NOW, ZoneOffset.UTC), loggingService);
    }

    private static Check check(int weight, CheckOutcome outcome, CheckCategory category) {
        return Check.builder().weight(weight).outcome(outcome).category(category).build();
    }

    @Nested
    class LockScore {

        @Test
        void insertsTheRowBeforeLockingIt() {
            when(cyberscoreRepository.insertIfAbsent(organizationId)).thenReturn(1);
            when(cyberscoreRepository.findByOrganizationIdForUpdate(organizationId))
                    .thenReturn(Optional.of(Cyberscore.builder().build()));

            service.lockScore(organizationId);

            InOrder order = inOrder(cyberscoreRepository);
            order.verify(cyberscoreRepository).insertIfAbsent(organizationId);
            order.verify(cyberscoreRepository).findByOrganizationIdForUpdate(organizationId);
        }

        @Test
        void rowStillMissingAfterInsert_throwsInternalServerError() {
            when(cyberscoreRepository.insertIfAbsent(organizationId)).thenReturn(0);
            when(cyberscoreRepository.findByOrganizationIdForUpdate(organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.lockScore(organizationId))
                    .isInstanceOf(InternalServerErrorException.class);
        }
    }

    @Nested
    class Recalculate {

        @Test
        void rowNotLocked_throwsInternalServerErrorAndWritesNothing() {
            when(cyberscoreRepository.findByOrganizationIdForUpdate(organizationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recalculate(organizationId,
                    CyberscoreSnapshotEventType.CHECK_CREATED, performedById))
                    .isInstanceOf(InternalServerErrorException.class);

            verify(cyberscoreRepository, never()).save(any());
            verify(snapshotRepository, never()).save(any());
        }

        @Test
        void resultMoved_storesScoreAndWritesOneSnapshot() {
            Cyberscore row = Cyberscore.builder().organizationId(organizationId).build();
            when(cyberscoreRepository.findByOrganizationIdForUpdate(organizationId)).thenReturn(Optional.of(row));
            when(checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId))
                    .thenReturn(List.of(check(1, CheckOutcome.PASS, CheckCategory.DEVICES),
                            check(1, CheckOutcome.FAIL, CheckCategory.DEVICES)));

            service.recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_OUTCOME_CHANGED, performedById);

            assertThat(row.getScorePercentage()).isEqualTo(50);
            assertThat(row.getScoreLevel()).isEqualTo(CyberscoreLevel.MEDIUM);
            assertThat(row.getBreakdownByCategory()).isEqualTo(Map.of("DEVICES", 50));
            assertThat(row.getLastCalculatedAt()).isEqualTo(NOW);
            verify(cyberscoreRepository).save(row);
            ArgumentCaptor<CyberscoreSnapshot> captor = ArgumentCaptor.forClass(CyberscoreSnapshot.class);
            verify(snapshotRepository).save(captor.capture());
            CyberscoreSnapshot snapshot = captor.getValue();
            assertThat(snapshot.getOrganizationId()).isEqualTo(organizationId);
            assertThat(snapshot.getScorePercentage()).isEqualTo(50);
            assertThat(snapshot.getScoreLevel()).isEqualTo(CyberscoreLevel.MEDIUM);
            assertThat(snapshot.getBreakdownByCategory()).isEqualTo(Map.of("DEVICES", 50));
            assertThat(snapshot.getEventType()).isEqualTo(CyberscoreSnapshotEventType.CHECK_OUTCOME_CHANGED);
            assertThat(snapshot.getPerformedById()).isEqualTo(performedById);
            assertThat(snapshot.getTakenAt()).isEqualTo(NOW);
        }

        @Test
        void resultUnchanged_refreshesTheTimestampButWritesNoSnapshot() {
            Cyberscore row = Cyberscore.builder().organizationId(organizationId).scorePercentage(100)
                    .scoreLevel(CyberscoreLevel.MINIMAL).breakdownByCategory(new HashMap<>(Map.of("DEVICES", 100)))
                    .lastCalculatedAt(NOW.minusSeconds(3600)).build();
            when(cyberscoreRepository.findByOrganizationIdForUpdate(organizationId)).thenReturn(Optional.of(row));
            when(checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId))
                    .thenReturn(List.of(check(5, CheckOutcome.PASS, CheckCategory.DEVICES)));

            service.recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_UPDATED, performedById);

            assertThat(row.getLastCalculatedAt()).isEqualTo(NOW);
            verify(snapshotRepository, never()).save(any());
        }

        @Test
        void onlyTheBreakdownMoved_writesASnapshot() {
            Cyberscore row = Cyberscore.builder().organizationId(organizationId).scorePercentage(50)
                    .scoreLevel(CyberscoreLevel.MEDIUM).breakdownByCategory(new HashMap<>(Map.of("DEVICES", 50)))
                    .lastCalculatedAt(NOW).build();
            when(cyberscoreRepository.findByOrganizationIdForUpdate(organizationId)).thenReturn(Optional.of(row));
            // Same overall 50, now split over two categories.
            when(checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId))
                    .thenReturn(List.of(check(1, CheckOutcome.PASS, CheckCategory.COMPLIANCE),
                            check(1, CheckOutcome.FAIL, CheckCategory.DEVICES)));

            service.recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_UPDATED, performedById);

            verify(snapshotRepository).save(any(CyberscoreSnapshot.class));
        }

        @Test
        void lastCheckDeleted_goesToUnknownAndWritesASnapshot() {
            Cyberscore row = Cyberscore.builder().organizationId(organizationId).scorePercentage(80)
                    .scoreLevel(CyberscoreLevel.LOW).lastCalculatedAt(NOW).build();
            when(cyberscoreRepository.findByOrganizationIdForUpdate(organizationId)).thenReturn(Optional.of(row));
            when(checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId))
                    .thenReturn(List.of());

            service.recalculate(organizationId, CyberscoreSnapshotEventType.CHECK_DELETED, performedById);

            assertThat(row.getScoreLevel()).isEqualTo(CyberscoreLevel.UNKNOWN);
            assertThat(row.getScorePercentage()).isZero();
            ArgumentCaptor<CyberscoreSnapshot> captor = ArgumentCaptor.forClass(CyberscoreSnapshot.class);
            verify(snapshotRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo(CyberscoreSnapshotEventType.CHECK_DELETED);
        }
    }
}
