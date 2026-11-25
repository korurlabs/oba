package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberKindSummaryProjection;
import com.korur.oba.shrine.organization.dto.projection.OrganizationMemberSummaryProjection;
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberQueryServiceImplUnitSummariesTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private OrganizationMemberQueryServiceImpl organizationMemberQueryServiceImpl;

    @Test
    void listActiveMemberSummaries_anyOrganization_passesCutoffToOneRepositoryQuery() {
        UUID organizationId = UUID.randomUUID();
        Instant joinedBefore = Instant.parse("2026-10-16T07:30:00Z");
        OrganizationMemberKindSummaryProjection summary = mock(OrganizationMemberKindSummaryProjection.class);
        when(organizationMemberRepository.findActiveMemberSummaries(organizationId, joinedBefore))
                .thenReturn(List.of(summary));

        List<OrganizationMemberSummaryProjection> result =
                organizationMemberQueryServiceImpl.listActiveMemberSummaries(organizationId, joinedBefore);

        assertThat(result).containsExactly(summary);
        verify(organizationMemberRepository).findActiveMemberSummaries(organizationId, joinedBefore);
        verifyNoMoreInteractions(organizationMemberRepository);
    }

    @Test
    void listActiveMemberKindSummaries_anyOrganization_returnsTheRowsOfTheSameQueryAgentsIncluded() {
        UUID organizationId = UUID.randomUUID();
        Instant joinedBefore = Instant.parse("2026-10-16T07:30:00Z");
        List<OrganizationMemberKindSummaryProjection> summaries =
                List.of(mock(OrganizationMemberKindSummaryProjection.class));
        when(organizationMemberRepository.findActiveMemberSummaries(organizationId, joinedBefore))
                .thenReturn(summaries);

        List<OrganizationMemberKindSummaryProjection> result =
                organizationMemberQueryServiceImpl.listActiveMemberKindSummaries(organizationId, joinedBefore);

        assertThat(result).isSameAs(summaries);
        verify(organizationMemberRepository).findActiveMemberSummaries(organizationId, joinedBefore);
        verifyNoMoreInteractions(organizationMemberRepository);
    }

    @Test
    void listActiveMemberSummaries_unknownOrganization_returnsEmptyList() {
        UUID organizationId = UUID.randomUUID();
        Instant joinedBefore = Instant.parse("2026-10-16T07:30:00Z");
        when(organizationMemberRepository.findActiveMemberSummaries(organizationId, joinedBefore))
                .thenReturn(List.of());

        assertThat(organizationMemberQueryServiceImpl.listActiveMemberSummaries(organizationId, joinedBefore))
                .isEmpty();
    }
}
