package com.korur.oba.connect.alertmanager.service.impl;

// 1. Framework-level
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 3. Java SDK
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerAlertRequest;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;
import com.korur.oba.connect.alertmanager.dto.response.AlertmanagerWebhookResponse;
import com.korur.oba.connect.alertmanager.service.AlertmanagerNotificationHandler;
import com.korur.oba.dojo.logging.service.LoggingService;

@ExtendWith(MockitoExtension.class)
class AlertmanagerWebhookServiceImplUnitTest {

    private final UUID organizationId = UUID.randomUUID();

    @Mock
    private AlertmanagerNotificationHandler handler;

    @Mock
    private LoggingService loggingService;

    private static AlertmanagerWebhookRequest request() {
        AlertmanagerAlertRequest alert =
                new AlertmanagerAlertRequest("firing", null, null, Instant.now(), null, null, "fp1");
        return new AlertmanagerWebhookRequest("4", null, null, null, null, null, null, null, null, List.of(alert));
    }

    @Test
    void processNotification_handsRequestToHandlerAndReturnsItsResponse() {
        AlertmanagerWebhookRequest request = request();
        AlertmanagerWebhookResponse expected = new AlertmanagerWebhookResponse(1, 2);
        when(handler.handle(organizationId, request)).thenReturn(expected);
        AlertmanagerWebhookServiceImpl service = new AlertmanagerWebhookServiceImpl(handler, loggingService);

        AlertmanagerWebhookResponse response = service.processNotification(organizationId, request);

        assertThat(response).isSameAs(expected);
        verify(handler).handle(organizationId, request);
    }

    @Test
    void processNotification_handlerThrows_propagatesException() {
        AlertmanagerWebhookRequest request = request();
        IllegalStateException failure = new IllegalStateException("handler failed");
        when(handler.handle(organizationId, request)).thenThrow(failure);
        AlertmanagerWebhookServiceImpl service = new AlertmanagerWebhookServiceImpl(handler, loggingService);

        assertThatThrownBy(() -> service.processNotification(organizationId, request)).isSameAs(failure);
    }
}
