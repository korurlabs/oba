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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Set;
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.access.constant.AccessConstant;
import com.korur.oba.shrine.access.constant.exception.AccessExceptionLogConstant;
import com.korur.oba.shrine.access.enums.App;
import com.korur.oba.shrine.access.service.AppAccessQueryService;
import com.korur.oba.shrine.access.validator.AppAccessValidator;
import com.korur.oba.shrine.organization.constant.OrganizationConstant;

@ExtendWith(MockitoExtension.class)
class AppAccessValidatorUnitTest {

    @Mock
    private AppAccessQueryService appAccessQueryService;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private AppAccessValidator appAccessValidator;

    @Nested
    class EnsureUsersHaveAppAccess {

        @Test
        void noUsers_passesWithoutAQuery() {
            // Act and assert
            assertDoesNotThrow(() -> appAccessValidator.ensureUsersHaveAppAccess(UUID.randomUUID(), App.KAIZEN,
                    List.of()));
            verifyNoInteractions(appAccessQueryService, loggingService);
        }

        @Test
        void everyUserHoldsTheApp_passes() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            List<UUID> userIds = List.of(first, second);
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAIZEN, userIds))
                    .thenReturn(Set.of(first, second));

            // Act and assert
            assertDoesNotThrow(() -> appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAIZEN,
                    userIds));
            verifyNoInteractions(loggingService);
        }

        @Test
        void oneUserWithoutTheApp_throwsMemberWithoutAppAccessNamingThatUserAndLogsWarn() {
            // Arrange
            UUID organizationId = UUID.randomUUID();
            UUID holder = UUID.randomUUID();
            UUID refused = UUID.randomUUID();
            List<UUID> userIds = List.of(holder, refused, refused);
            when(appAccessQueryService.findUserIdsWithAppAccess(organizationId, App.KAIZEN, userIds))
                    .thenReturn(Set.of(holder));

            // Act
            ValidationException exception = assertThrows(ValidationException.class,
                    () -> appAccessValidator.ensureUsersHaveAppAccess(organizationId, App.KAIZEN, userIds));

            // Assert
            assertEquals("APP_VAL_002", exception.getErrorCode());
            assertEquals(refused, exception.getErrorValues().get(AccessConstant.FIELD_USER_ID));
            assertEquals("KAIZEN", exception.getErrorValues().get(AccessConstant.FIELD_APP));
            assertEquals(organizationId, exception.getErrorValues().get(OrganizationConstant.FIELD_ORGANIZATION_ID));
            verify(loggingService).warn(any(), eq(LogLayer.VALIDATOR),
                    eq(AccessExceptionLogConstant.MEMBER_WITHOUT_APP_ACCESS), eq(organizationId), eq(App.KAIZEN),
                    eq(List.of(refused)));
        }
    }
}
