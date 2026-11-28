package com.korur.oba.connect.azure.client;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 2. Third-party
import com.azure.core.credential.TokenCredential;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.mock_data.AzureConnectionMocks;
import com.korur.oba.connect.oidc.service.OidcAssertionService;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AzureConnectionTokenProviderUnitTest {

    private final OidcAssertionService oidcAssertionService = mock(OidcAssertionService.class);
    private final AzureConnectionTokenProvider provider =
            new AzureConnectionTokenProvider(oidcAssertionService, mock(LoggingService.class));

    @Test
    void credentialFor_sameConnectionTwice_reusesOneCredential() {
        AzureConnection connection = AzureConnectionMocks.connection();

        TokenCredential first = provider.credentialFor(connection);
        TokenCredential second = provider.credentialFor(connection);

        assertThat(second).isSameAs(first);
        // Building a credential signs nothing: an assertion is signed only for a token exchange.
        verifyNoInteractions(oidcAssertionService);
    }

    @Test
    void credentialFor_clientIdChanged_rebuildsTheCredential() {
        AzureConnection connection = AzureConnectionMocks.connection();
        TokenCredential first = provider.credentialFor(connection);

        connection.setClientId(UUID.randomUUID());
        TokenCredential second = provider.credentialFor(connection);

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void credentialFor_tenantIdChanged_rebuildsTheCredential() {
        AzureConnection connection = AzureConnectionMocks.connection();
        TokenCredential first = provider.credentialFor(connection);

        connection.setTenantId(UUID.randomUUID());

        assertThat(provider.credentialFor(connection)).isNotSameAs(first);
    }

    @Test
    void credentialFor_afterEvict_buildsANewCredential() {
        AzureConnection connection = AzureConnectionMocks.connection();
        TokenCredential first = provider.credentialFor(connection);

        provider.evict(connection.getId());

        assertThat(provider.credentialFor(connection)).isNotSameAs(first);
    }

    @Test
    void credentialFor_twoConnections_getTheirOwnCredential() {
        TokenCredential first = provider.credentialFor(AzureConnectionMocks.connection());
        TokenCredential second = provider.credentialFor(AzureConnectionMocks.connection());

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void getAccessToken_noClientId_isClientIdMissing() {
        AzureConnection connection = AzureConnectionMocks.connection(null);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> provider.getAccessToken(connection));

        assertThat(exception.getErrorCode())
                .isEqualTo(ExceptionDomainAzureError.VAL_AZURE_CONNECTION_CLIENT_ID_MISSING.getCode());
        verifyNoInteractions(oidcAssertionService);
    }
}
