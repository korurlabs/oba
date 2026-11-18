package com.korur.oba.apps.otak.battlefield.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.relation.dto.response.RelationListResponse;
import com.korur.oba.apps.otak.relation.service.RelationQueryService;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class BattlefieldQueryServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private RelationQueryService relationQueryService;

    @Mock
    private LoggingService loggingService;

    private BattlefieldQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BattlefieldQueryServiceImpl(relationQueryService, loggingService);
    }

    @Test
    void getBattlefield_organizationGiven_returnsTheLeadsOfTheRelationDomain() {
        // Arrange
        List<RelationListResponse> leads = List.of(mock(RelationListResponse.class));
        when(relationQueryService.listPipeline(organizationId)).thenReturn(leads);

        // Act
        List<RelationListResponse> result = service.getBattlefield(organizationId);

        // Assert
        assertThat(result).isSameAs(leads);
        verify(relationQueryService).listPipeline(organizationId);
        verifyNoMoreInteractions(relationQueryService);
    }

    @Test
    void getBattlefield_noLeads_returnsEmptyList() {
        // Arrange
        when(relationQueryService.listPipeline(organizationId)).thenReturn(List.of());

        // Act
        List<RelationListResponse> result = service.getBattlefield(organizationId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void service_readsInAReadOnlyTransaction() {
        Transactional transactional = BattlefieldQueryServiceImpl.class.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
