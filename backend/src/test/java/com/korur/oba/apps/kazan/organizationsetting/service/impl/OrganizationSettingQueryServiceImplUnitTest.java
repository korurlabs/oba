package com.korur.oba.apps.kazan.organizationsetting.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.organizationsetting.entity.OrganizationSetting;
import com.korur.oba.apps.kazan.organizationsetting.repository.OrganizationSettingRepository;

@ExtendWith(MockitoExtension.class)
class OrganizationSettingQueryServiceImplUnitTest {

    @Mock
    private OrganizationSettingRepository organizationSettingRepository;

    @InjectMocks
    private OrganizationSettingQueryServiceImpl organizationSettingQueryServiceImpl;

    @Test
    void getSecurityBoardId_rowWithBoard_returnsBoardId() {
        // Arrange
        UUID organizationId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        when(organizationSettingRepository.findById(organizationId)).thenReturn(Optional.of(
                OrganizationSetting.builder().organizationId(organizationId).securityBoardId(boardId).build()));

        // Act
        Optional<UUID> result = organizationSettingQueryServiceImpl.getSecurityBoardId(organizationId);

        // Assert
        assertEquals(Optional.of(boardId), result);
    }

    @Test
    void getSecurityBoardId_rowWithoutBoard_returnsEmpty() {
        // Arrange
        UUID organizationId = UUID.randomUUID();
        when(organizationSettingRepository.findById(organizationId)).thenReturn(Optional.of(
                OrganizationSetting.builder().organizationId(organizationId).build()));

        // Act
        Optional<UUID> result = organizationSettingQueryServiceImpl.getSecurityBoardId(organizationId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getSecurityBoardId_noRow_returnsEmpty() {
        // Arrange
        UUID organizationId = UUID.randomUUID();
        when(organizationSettingRepository.findById(organizationId)).thenReturn(Optional.empty());

        // Act
        Optional<UUID> result = organizationSettingQueryServiceImpl.getSecurityBoardId(organizationId);

        // Assert
        assertTrue(result.isEmpty());
    }
}
