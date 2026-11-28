package com.korur.oba.connect.azure.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.client.AzureResourceManagerClient;
import com.korur.oba.connect.azure.dto.AzureSubscriptionPayload;
import com.korur.oba.connect.azure.entity.AzureConnection;
import com.korur.oba.connect.azure.exception.ExceptionDomainAzureError;
import com.korur.oba.connect.azure.mock_data.AzureConnectionMocks;
import com.korur.oba.connect.azure.repository.AzureConnectionRepository;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureResourceManagerQueryServiceImplUnitTest {

    private final AzureConnectionRepository repository = mock(AzureConnectionRepository.class);
    private final AzureResourceManagerClient client = mock(AzureResourceManagerClient.class);
    private final AzureResourceManagerQueryServiceImpl service =
            new AzureResourceManagerQueryServiceImpl(repository, client, mock(LoggingService.class));

    @Test
    void listSubscriptions_knownConnection_readsAsThatConnection() {
        AzureConnection connection = AzureConnectionMocks.connection();
        List<AzureSubscriptionPayload> subscriptions =
                List.of(new AzureSubscriptionPayload(UUID.randomUUID(), "Production", "Enabled"));
        when(repository.findById(connection.getId())).thenReturn(Optional.of(connection));
        when(client.listSubscriptions(connection)).thenReturn(subscriptions);

        List<AzureSubscriptionPayload> result = service.listSubscriptions(connection.getId());

        assertThat(result).isEqualTo(subscriptions);
    }

    @Test
    void listSubscriptions_unknownConnection_isNotFoundWithoutAzureCall() {
        UUID connectionId = UUID.randomUUID();
        when(repository.findById(connectionId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> service.listSubscriptions(connectionId));

        assertThat(exception.getErrorCode()).isEqualTo(ExceptionDomainAzureError.NOT_AZURE_CONNECTION_FOUND.getCode());
        verify(client, never()).listSubscriptions(any());
    }
}
