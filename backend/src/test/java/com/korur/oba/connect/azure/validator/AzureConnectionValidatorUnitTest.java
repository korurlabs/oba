package com.korur.oba.connect.azure.validator;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 2. Third-party

// 3. Java SDK
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.enums.AzureConnectionStatus;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.mock_data.AzureConnectionMocks;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.connect.oidc.service.OidcAssertionService;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureConnectionValidatorUnitTest {

    private final UUID organizationId = UUID.randomUUID();
    private final UUID connectionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    private final AzureConnectionRepository repository = mock(AzureConnectionRepository.class);
    private final OidcAssertionService oidcAssertionService = mock(OidcAssertionService.class);
    private final AzureConnectionValidator validator =
            new AzureConnectionValidator(repository, oidcAssertionService, mock(LoggingService.class));

    @Test
    void ensureIssuerConfigured_issuerSet_passes() {
        when(oidcAssertionService.isConfigured()).thenReturn(true);

        assertThatCode(() -> validator.ensureIssuerConfigured(organizationId)).doesNotThrowAnyException();
    }

    @Test
    void ensureIssuerConfigured_noIssuer_isNotFoundUnavailable() {
        when(oidcAssertionService.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> validator.ensureIssuerConfigured(organizationId))
                .isInstanceOfSatisfying(NotFoundException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.NOT_AZURE_CONNECTIONS_UNAVAILABLE.getCode()));
    }

    @Test
    void ensureConnectionExists_connectionOfTheOrganization_returnsIt() {
        AzureConnection connection = AzureConnectionMocks.connection();
        when(repository.findByIdAndOrganizationId(connectionId, organizationId)).thenReturn(Optional.of(connection));

        assertThat(validator.ensureConnectionExists(organizationId, connectionId)).isSameAs(connection);
    }

    @Test
    void ensureConnectionExists_connectionOfAnotherOrganization_isNotFound() {
        when(repository.findByIdAndOrganizationId(connectionId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.ensureConnectionExists(organizationId, connectionId))
                .isInstanceOfSatisfying(NotFoundException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.NOT_AZURE_CONNECTION_FOUND.getCode()));
    }

    @Test
    void ensureNameIsUnique_nameFree_passes() {
        when(repository.existsByOrganizationIdAndName(organizationId, "Production")).thenReturn(false);

        assertThatCode(() -> validator.ensureNameIsUnique(organizationId, "Production")).doesNotThrowAnyException();
    }

    @Test
    void ensureNameIsUnique_nameTaken_isConflict() {
        when(repository.existsByOrganizationIdAndName(organizationId, "Production")).thenReturn(true);

        assertThatThrownBy(() -> validator.ensureNameIsUnique(organizationId, "Production"))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NAME_DUPLICATE.getCode()));
    }

    @Test
    void ensureNameIsUnique_editKeepsOwnName_passes() {
        when(repository.existsByOrganizationIdAndNameAndIdNot(organizationId, "Production", connectionId))
                .thenReturn(false);

        assertThatCode(() -> validator.ensureNameIsUnique(organizationId, "Production", connectionId))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureNameIsUnique_editTakesAnotherConnectionsName_isConflict() {
        when(repository.existsByOrganizationIdAndNameAndIdNot(organizationId, "Production", connectionId))
                .thenReturn(true);

        assertThatThrownBy(() -> validator.ensureNameIsUnique(organizationId, "Production", connectionId))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NAME_DUPLICATE.getCode()));
    }

    @Test
    void ensureIdentityIsNew_noClientId_passesWithoutQuery() {
        assertThatCode(() -> validator.ensureIdentityIsNew(organizationId, tenantId, null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.ensureIdentityIsNew(organizationId, tenantId, null, connectionId))
                .doesNotThrowAnyException();

        verify(repository, never()).existsByOrganizationIdAndTenantIdAndClientId(any(), any(), any());
        verify(repository, never()).existsByOrganizationIdAndTenantIdAndClientIdAndIdNot(any(), any(), any(), any());
    }

    @Test
    void ensureIdentityIsNew_identityFree_passes() {
        when(repository.existsByOrganizationIdAndTenantIdAndClientId(organizationId, tenantId, clientId))
                .thenReturn(false);

        assertThatCode(() -> validator.ensureIdentityIsNew(organizationId, tenantId, clientId))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureIdentityIsNew_identityConnected_isConflict() {
        when(repository.existsByOrganizationIdAndTenantIdAndClientId(organizationId, tenantId, clientId))
                .thenReturn(true);

        assertThatThrownBy(() -> validator.ensureIdentityIsNew(organizationId, tenantId, clientId))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_IDENTITY_DUPLICATE.getCode()));
    }

    @Test
    void ensureIdentityIsNew_editTakesAnotherConnectionsIdentity_isConflict() {
        when(repository.existsByOrganizationIdAndTenantIdAndClientIdAndIdNot(organizationId, tenantId, clientId,
                connectionId)).thenReturn(true);

        assertThatThrownBy(() -> validator.ensureIdentityIsNew(organizationId, tenantId, clientId, connectionId))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_IDENTITY_DUPLICATE.getCode()));
    }

    @Test
    void ensureIdentityIsNew_editKeepsOwnIdentity_passes() {
        when(repository.existsByOrganizationIdAndTenantIdAndClientIdAndIdNot(organizationId, tenantId, clientId,
                connectionId)).thenReturn(false);

        assertThatCode(() -> validator.ensureIdentityIsNew(organizationId, tenantId, clientId, connectionId))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureClientIdSet_clientIdSet_passes() {
        AzureConnection connection = AzureConnectionMocks.connection();

        assertThatCode(() -> validator.ensureClientIdSet(connection)).doesNotThrowAnyException();
    }

    @Test
    void ensureClientIdSet_noClientId_isValidationError() {
        AzureConnection connection = AzureConnectionMocks.connection(null);

        assertThatThrownBy(() -> validator.ensureClientIdSet(connection))
                .isInstanceOfSatisfying(ValidationException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.VAL_AZURE_CONNECTION_CLIENT_ID_MISSING.getCode()));
    }

    @Test
    void ensureConnected_connected_passes() {
        AzureConnection connection = AzureConnectionMocks.connection();
        connection.setStatus(AzureConnectionStatus.CONNECTED);

        assertThatCode(() -> validator.ensureConnected(connection)).doesNotThrowAnyException();
    }

    @Test
    void ensureConnected_pendingOrFailing_isConflict() {
        AzureConnection pending = AzureConnectionMocks.connection();
        AzureConnection failing = AzureConnectionMocks.connection();
        failing.setStatus(AzureConnectionStatus.FAILING);

        assertThatThrownBy(() -> validator.ensureConnected(pending))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NOT_CONNECTED.getCode()));
        assertThatThrownBy(() -> validator.ensureConnected(failing))
                .isInstanceOfSatisfying(ConflictException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ExceptionDomainAzureError.STA_AZURE_CONNECTION_NOT_CONNECTED.getCode()));
    }
}
