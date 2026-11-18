package com.korur.oba.shrine.access.validator.unit;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.dto.request.AppGrantCreateRequest;
import com.korur.oba.shrine.access.entity.AppGrant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.repository.AppGrantRepository;
import com.korur.oba.shrine.access.validator.AppGrantValidator;

@ExtendWith(MockitoExtension.class)
class AppGrantValidatorUnitTest {

    @Mock
    private AppGrantRepository appGrantRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private AppGrantValidator appGrantValidator;

    @Nested
    class EnsureOneTarget {

        @Test
        void userOnly_passes() {
            // Arrange
            AppGrantCreateRequest request = new AppGrantCreateRequest(App.KAIZEN, UUID.randomUUID(), null);

            // Act and assert
            assertDoesNotThrow(() -> appGrantValidator.ensureOneTarget(UUID.randomUUID(), request));
            verifyNoInteractions(loggingService);
        }

        @Test
        void groupOnly_passes() {
            // Arrange
            AppGrantCreateRequest request = new AppGrantCreateRequest(App.KAYA, null, UUID.randomUUID());

            // Act and assert
            assertDoesNotThrow(() -> appGrantValidator.ensureOneTarget(UUID.randomUUID(), request));
            verifyNoInteractions(loggingService);
        }

        @Test
        void noTarget_throwsGrantTargetInvalidAndLogsWarn() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            AppGrantCreateRequest request = new AppGrantCreateRequest(App.KAIZEN, null, null);

            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> appGrantValidator.ensureOneTarget(organizationId, request));

            // Assert
            assertEquals("APP_VAL_001", exception.getErrorCode());
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(AccessExceptionLogConstant.GRANT_TARGET_INVALID), eq(organizationId));
        }

        @Test
        void twoTargets_throwsGrantTargetInvalid() {
            // Arrange
            AppGrantCreateRequest request =
                    new AppGrantCreateRequest(App.KAIZEN, UUID.randomUUID(), UUID.randomUUID());

            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> appGrantValidator.ensureOneTarget(UUID.randomUUID(), request));

            // Assert
            assertEquals("APP_VAL_001", exception.getErrorCode());
        }
    }

    @Nested
    class EnsureGrantExists {

        @Test
        void grantOfTheOrganization_returnsTheGrant() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID grantId = UUID.randomUUID();
            AppGrant grant = AppGrant.builder().app(App.KAIZEN).build();
            when(appGrantRepository.findByIdAndOrganization_Id(grantId, organizationId))
                    .thenReturn(Optional.of(grant));

            // Act
            AppGrant result = appGrantValidator.ensureGrantExists(organizationId, grantId);

            // Assert
            assertSame(grant, result);
            verifyNoInteractions(loggingService);
        }

        @Test
        void grantMissingOrOfAnotherOrganization_throwsGrantNotFoundAndLogsWarn() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID grantId = UUID.randomUUID();
            when(appGrantRepository.findByIdAndOrganization_Id(grantId, organizationId))
                    .thenReturn(Optional.empty());

            // Act
            NotFoundException exception = assertThrows(NotFoundException.class,
                    () -> appGrantValidator.ensureGrantExists(organizationId, grantId));

            // Assert
            assertEquals("APP_NOT_002", exception.getErrorCode());
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(AccessExceptionLogConstant.GRANT_NOT_FOUND), eq(organizationId), eq(grantId));
        }
    }
}
