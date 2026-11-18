package com.korur.oba.shrine.organization.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.shrine.organization.repository.OrganizationMemberRepository;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberQueryServiceImplUnitPrimaryOrganizationTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private OrganizationMemberQueryServiceImpl organizationMemberQueryServiceImpl;

    @Test
    void findPrimaryActiveOrganizationId_userWithActiveMembership_returnsTheOrganizationOfOneQuery() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        when(organizationMemberRepository.findPrimaryActiveOrganizationId(userId))
                .thenReturn(Optional.of(organizationId));

        // Act
        Optional<UUID> result = organizationMemberQueryServiceImpl.findPrimaryActiveOrganizationId(userId);

        // Assert
        assertThat(result).contains(organizationId);
        verify(organizationMemberRepository).findPrimaryActiveOrganizationId(userId);
        verifyNoMoreInteractions(organizationMemberRepository);
    }

    @Test
    void findPrimaryActiveOrganizationId_userWithoutActiveMembership_returnsEmpty() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(organizationMemberRepository.findPrimaryActiveOrganizationId(userId)).thenReturn(Optional.empty());

        // Act
        Optional<UUID> result = organizationMemberQueryServiceImpl.findPrimaryActiveOrganizationId(userId);

        // Assert
        assertThat(result).isEmpty();
    }
}
