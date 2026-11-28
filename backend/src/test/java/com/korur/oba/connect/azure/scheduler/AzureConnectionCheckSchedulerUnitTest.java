package com.korur.oba.connect.azure.scheduler;

// 1. Framework-level
import org.junit.jupiter.api.Test;

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.azure.service.AzureConnectionCheckService;
import com.korur.oba.connect.azure.service.AzureConnectionQueryService;
import com.korur.oba.dojo.logging.service.LoggingService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureConnectionCheckSchedulerUnitTest {

    private final AzureConnectionQueryService queryService = mock(AzureConnectionQueryService.class);
    private final AzureConnectionCheckService checkService = mock(AzureConnectionCheckService.class);
    private final AzureConnectionCheckScheduler scheduler =
            new AzureConnectionCheckScheduler(queryService, checkService, mock(LoggingService.class));

    @Test
    void checkConnections_oneConnectionFails_stillChecksTheOthers() {
        UUID failing = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        when(queryService.listCheckableConnectionIds()).thenReturn(List.of(failing, next));
        doThrow(new IllegalStateException("database down")).when(checkService).checkConnection(failing);

        scheduler.checkConnections();

        verify(checkService).checkConnection(failing);
        verify(checkService).checkConnection(next);
    }

    @Test
    void checkConnections_noCheckableConnection_checksNothing() {
        when(queryService.listCheckableConnectionIds()).thenReturn(List.of());

        scheduler.checkConnections();

        verify(checkService, never()).checkConnection(any());
    }
}
