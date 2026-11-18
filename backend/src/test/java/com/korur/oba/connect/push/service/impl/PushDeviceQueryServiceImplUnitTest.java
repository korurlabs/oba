package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.entity.PushDevice;
import com.korur.oba.connect.push.repository.PushDeviceRepository;

@ExtendWith(MockitoExtension.class)
class PushDeviceQueryServiceImplUnitTest {

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    @InjectMocks
    private PushDeviceQueryServiceImpl service;

    @Test
    void getDevicesByUserIds_userIds_returnsTheirDevices() {
        // Arrange
        Set<UUID> userIds = Set.of(UUID.randomUUID());
        List<PushDevice> devices = List.of(PushDevice.builder().build());
        when(pushDeviceRepository.findAllByUserIdIn(userIds)).thenReturn(devices);

        // Act
        List<PushDevice> result = service.getDevicesByUserIds(userIds);

        // Assert
        assertThat(result).isEqualTo(devices);
        verify(pushDeviceRepository).findAllByUserIdIn(userIds);
    }

    @Test
    void getDevicesByUserIds_emptyOrNull_returnsEmptyWithoutAQuery() {
        // Act and assert
        assertThat(service.getDevicesByUserIds(Set.of())).isEmpty();
        assertThat(service.getDevicesByUserIds(null)).isEmpty();
        verifyNoInteractions(pushDeviceRepository);
    }
}
