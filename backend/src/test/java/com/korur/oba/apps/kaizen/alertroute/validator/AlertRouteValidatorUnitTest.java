package com.korur.oba.apps.kaizen.alertroute.validator;

// 1. Framework-level
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.alertroute.constant.AlertRouteExceptionLogConstant;
import com.korur.oba.apps.kaizen.alertroute.repository.AlertRouteRepository;
import com.korur.oba.dojo.exception.internal.auth.ForbiddenException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.shrine.organization.entity.Organization;
import com.korur.oba.shrine.organization.enums.OrganizationStatus;
import com.korur.oba.shrine.organization.exception.ExceptionDomainOrganizationError;
import com.korur.oba.shrine.organization.service.OrganizationQueryService;

@ExtendWith(MockitoExtension.class)
class AlertRouteValidatorUnitTest {

    @Mock
    private AlertRouteRepository alertRouteRepository;

    @Mock
    private OrganizationQueryService organizationQueryService;

    @Mock
    private LoggingService loggingService;

    private AlertRouteValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AlertRouteValidator(alertRouteRepository, organizationQueryService, loggingService);
    }

    @Nested
    class EnsureOrganizationIsActive {

        private final UUID organizationId = UUID.randomUUID();

        @Test
        void activeOrganization_doesNotThrowAndLogsNothing() {
            // Arrange
            when(organizationQueryService.getOrganizationEntityById(organizationId))
                    .thenReturn(Organization.builder().status(OrganizationStatus.ACTIVE).build());

            // Act & Assert
            assertThatCode(() -> validator.ensureOrganizationIsActive(organizationId)).doesNotThrowAnyException();
            verifyNoInteractions(loggingService, alertRouteRepository);
        }

        @Test
        void unknownOrganization_rethrowsNotFoundAndLogsError() {
            // Arrange
            NotFoundException notFound = new NotFoundException(ExceptionDomainOrganizationError.NOT_ORGANIZATION_FOUND);
            when(organizationQueryService.getOrganizationEntityById(organizationId)).thenThrow(notFound);

            // Act & Assert
            assertThatThrownBy(() -> validator.ensureOrganizationIsActive(organizationId)).isSameAs(notFound);
            verify(loggingService).error(any(), eq(LogLayer.VALIDATOR),
                    eq(AlertRouteExceptionLogConstant.ORGANIZATION_NOT_FOUND), eq(organizationId));
        }

        @Test
        void disabledOrganization_throwsOrganizationDisabledAndLogsError() {
            // Arrange
            when(organizationQueryService.getOrganizationEntityById(organizationId))
                    .thenReturn(Organization.builder().status(OrganizationStatus.DISABLED).build());

            // Act & Assert
            assertThatThrownBy(() -> validator.ensureOrganizationIsActive(organizationId))
                    .isInstanceOfSatisfying(ForbiddenException.class, (ForbiddenException exception) ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ExceptionDomainOrganizationError.SEC_ORGANIZATION_DISABLED.getCode()));
            verify(loggingService).error(any(), eq(LogLayer.VALIDATOR),
                    eq(AlertRouteExceptionLogConstant.ORGANIZATION_DISABLED), eq(organizationId),
                    eq(OrganizationStatus.DISABLED));
        }
    }
}
