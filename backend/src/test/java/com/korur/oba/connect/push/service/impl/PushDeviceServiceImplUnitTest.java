package com.korur.oba.connect.push.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.push.constant.exception.PushExceptionLogConstant;
import com.korur.oba.connect.push.constant.service.PushDeviceServiceLogConstant;
import com.korur.oba.connect.push.dto.request.PushDeviceRegisterRequest;
import com.korur.oba.connect.push.dto.response.PushDeviceResponse;
import com.korur.oba.connect.push.entity.PushDevice;
import com.korur.oba.connect.push.enums.PushPlatform;
import com.korur.oba.connect.push.mapper.PushDeviceMapperImpl;
import com.korur.oba.connect.push.repository.PushDeviceRepository;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class PushDeviceServiceImplUnitTest {

    private static final String TOKEN = "ExponentPushToken[abc123]";
    private static final Instant NOW = Instant.parse("2026-11-06T09:00:00Z");

    @Mock
    private PushDeviceRepository pushDeviceRepository;
    @Mock
    private LoggingService loggingService;

    private PushDeviceServiceImpl service;

    private final User user = userWithId();
    private final User otherUser = userWithId();

    @BeforeEach
    void setUp() {
        service = new PushDeviceServiceImpl(pushDeviceRepository, new PushDeviceMapperImpl(), loggingService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User userWithId() {
        User created = User.builder().email("user@example.com").build();
        created.setId(UUID.randomUUID());
        return created;
    }

    private PushDevice device(UUID ownerId, Instant registeredAt) {
        PushDevice device = PushDevice.builder()
                .expoPushToken(TOKEN)
                .platform(PushPlatform.IOS)
                .deviceName("old name")
                .appVersion("0.9.0")
                .locale("en")
                .lastRegisteredAt(registeredAt)
                .userId(ownerId)
                .build();
        device.setId(UUID.randomUUID());
        return device;
    }

    private PushDeviceRegisterRequest request() {
        return new PushDeviceRegisterRequest(TOKEN, PushPlatform.ANDROID, "Ayse's phone", "1.0.3", "tr");
    }

    private void givenSaveReturnsArgument() {
        when(pushDeviceRepository.save(any(PushDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    class RegisterDevice {

        @Test
        void newToken_createsTheRowAndReturnsItWithoutTheToken() {
            // Arrange
            PushDevice created = device(user.getId(), NOW);
            when(pushDeviceRepository.findByExpoPushToken(TOKEN)).thenReturn(Optional.of(created));
            givenSaveReturnsArgument();

            // Act
            PushDeviceResponse response = service.registerDevice(user, request());

            // Assert
            verify(pushDeviceRepository).insertIfAbsent(TOKEN, "ANDROID", "Ayse's phone", "1.0.3", "tr", NOW,
                    user.getId());
            assertThat(response.id()).isEqualTo(created.getId());
            assertThat(response.platform()).isEqualTo(PushPlatform.ANDROID);
            assertThat(response.deviceName()).isEqualTo("Ayse's phone");
            assertThat(response.appVersion()).isEqualTo("1.0.3");
            assertThat(response.lastRegisteredAt()).isEqualTo(NOW);
            verify(loggingService).info(any(), eq(LogLayer.SERVICE),
                    eq(PushDeviceServiceLogConstant.DEVICE_REGISTERED), any(Object[].class));
        }

        @Test
        void sameUserAgain_updatesLastRegisteredAtAndTheFieldsWithoutMovingTheDevice() {
            // Arrange
            Instant earlier = NOW.minusSeconds(86_400);
            PushDevice existing = device(user.getId(), earlier);
            when(pushDeviceRepository.findByExpoPushToken(TOKEN)).thenReturn(Optional.of(existing));
            givenSaveReturnsArgument();

            // Act
            PushDeviceResponse response = service.registerDevice(user, request());

            // Assert
            assertThat(response.lastRegisteredAt()).isEqualTo(NOW);
            assertThat(existing.getLastRegisteredAt()).isEqualTo(NOW);
            assertThat(existing.getAppVersion()).isEqualTo("1.0.3");
            assertThat(existing.getLocale()).isEqualTo("tr");
            assertThat(existing.getPlatform()).isEqualTo(PushPlatform.ANDROID);
            assertThat(existing.getUserId()).isEqualTo(user.getId());
            verify(loggingService, never()).info(any(), any(), eq(PushDeviceServiceLogConstant.DEVICE_MOVED),
                    any(Object[].class));
        }

        @Test
        void tokenOfAnotherUser_movesTheDeviceToTheCallerAndLogsTheMove() {
            // Arrange
            PushDevice existing = device(otherUser.getId(), NOW.minusSeconds(60));
            when(pushDeviceRepository.findByExpoPushToken(TOKEN)).thenReturn(Optional.of(existing));
            givenSaveReturnsArgument();

            // Act
            service.registerDevice(user, request());

            // Assert
            assertThat(existing.getUserId()).isEqualTo(user.getId());
            verify(pushDeviceRepository).save(existing);
            verify(loggingService).info(any(), eq(LogLayer.SERVICE), eq(PushDeviceServiceLogConstant.DEVICE_MOVED),
                    any(Object[].class));
        }

        @Test
        void rowMissingAfterTheInsert_throwsNotFound() {
            // Arrange
            when(pushDeviceRepository.findByExpoPushToken(TOKEN)).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.registerDevice(user, request()))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "PSH_NOT_001");
            verify(pushDeviceRepository, never()).save(any(PushDevice.class));
        }
    }

    @Nested
    class UnregisterDevice {

        @Test
        void ownDevice_deletesIt() {
            // Arrange
            PushDevice existing = device(user.getId(), NOW);
            when(pushDeviceRepository.findByIdAndUserId(existing.getId(), user.getId()))
                    .thenReturn(Optional.of(existing));

            // Act
            service.unregisterDevice(user, existing.getId());

            // Assert
            verify(pushDeviceRepository).delete(existing);
        }

        @Test
        void deviceOfAnotherUser_throwsNotFoundAndDeletesNothing() {
            // Arrange
            UUID deviceId = UUID.randomUUID();
            when(pushDeviceRepository.findByIdAndUserId(deviceId, user.getId())).thenReturn(Optional.empty());

            // Act and assert
            assertThatThrownBy(() -> service.unregisterDevice(user, deviceId))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", "PSH_NOT_001");
            verify(pushDeviceRepository, never()).delete(any(PushDevice.class));
            verify(loggingService).warn(any(), eq(LogLayer.SERVICE), eq(PushExceptionLogConstant.DEVICE_NOT_FOUND),
                    any(Object[].class));
        }
    }

    @Nested
    class RemoveTokens {

        @Test
        void tokens_deletesTheirRows() {
            // Arrange
            List<String> tokens = List.of(TOKEN, "ExponentPushToken[def]");
            when(pushDeviceRepository.deleteAllByExpoPushTokenIn(tokens)).thenReturn(2);

            // Act
            service.removeTokens(tokens);

            // Assert
            verify(pushDeviceRepository).deleteAllByExpoPushTokenIn(tokens);
        }

        @Test
        void emptyOrNullTokens_touchTheRepositoryNever() {
            // Act
            service.removeTokens(List.of());
            service.removeTokens(null);

            // Assert
            verify(pushDeviceRepository, never()).deleteAllByExpoPushTokenIn(any());
        }
    }
}
