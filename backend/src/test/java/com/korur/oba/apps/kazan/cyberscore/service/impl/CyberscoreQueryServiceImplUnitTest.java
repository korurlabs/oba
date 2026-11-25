package com.korur.oba.apps.kazan.cyberscore.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.entity.CyberscoreCheck;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckCategory;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreCheckOutcome;
import com.korur.oba.apps.kazan.cyberscore.enums.CyberscoreLevel;
import com.korur.oba.apps.kazan.cyberscore.mapper.CyberscoreMapperImpl;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreCheckRepository;
import com.korur.oba.apps.kazan.cyberscore.repository.CyberscoreRepository;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreTrendService;

@ExtendWith(MockitoExtension.class)
class CyberscoreQueryServiceImplUnitTest {

    private static final Instant CALCULATED_AT = Instant.parse("2026-11-25T10:00:00Z");

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private CyberscoreRepository cyberscoreRepository;

    @Mock
    private CyberscoreCheckRepository checkRepository;

    @Mock
    private CyberscoreTrendService cyberscoreTrendService;

    private CyberscoreQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CyberscoreQueryServiceImpl(cyberscoreRepository, checkRepository,
                new CyberscoreCalculateServiceImpl(), cyberscoreTrendService, new CyberscoreMapperImpl());
    }

    private static CyberscoreCheck check(int weight, CyberscoreCheckOutcome outcome,
                                         CyberscoreCheckCategory category) {
        return CyberscoreCheck.builder().weight(weight).outcome(outcome).category(category).build();
    }

    @Test
    void getCurrent_noScoreRow_returnsTheNotYetScoredShapeWithoutReadingChecks() {
        when(cyberscoreRepository.findByOrganizationId(organizationId)).thenReturn(Optional.empty());

        CyberscoreResponse response = service.getCurrent(organizationId);

        assertThat(response).isEqualTo(new CyberscoreResponse(0, CyberscoreLevel.UNKNOWN, Map.of(), 0, null));
        verify(checkRepository, never()).findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId);
    }

    @Test
    void getCurrent_scoredRow_takesScoreFromTheRowAndComputesTheBreakdownFromTheChecks() {
        Cyberscore row = Cyberscore.builder().organizationId(organizationId).scorePercentage(67)
                .scoreLevel(CyberscoreLevel.LOW).lastCalculatedAt(CALCULATED_AT).build();
        when(cyberscoreRepository.findByOrganizationId(organizationId)).thenReturn(Optional.of(row));
        when(checkRepository.findByOrganizationIdOrderByCategoryAscSortOrderAscNameAsc(organizationId))
                .thenReturn(List.of(
                        check(1, CyberscoreCheckOutcome.PASS, CyberscoreCheckCategory.IDENTITY),
                        check(1, CyberscoreCheckOutcome.PARTIAL, CyberscoreCheckCategory.DEVICES),
                        check(1, CyberscoreCheckOutcome.PASS, null),
                        check(5, CyberscoreCheckOutcome.NOT_APPLICABLE, CyberscoreCheckCategory.DATA)));

        CyberscoreResponse response = service.getCurrent(organizationId);

        assertThat(response.scorePercentage()).isEqualTo(67);
        assertThat(response.scoreLevel()).isEqualTo(CyberscoreLevel.LOW);
        assertThat(response.breakdownByCategory()).containsExactly(Map.entry("IDENTITY", 100),
                Map.entry("DEVICES", 50));
        assertThat(response.scorableCheckCount()).isEqualTo(3);
        assertThat(response.lastCalculatedAt()).isEqualTo(CALCULATED_AT);
    }
}
