package com.korur.oba.connect.alertmanager.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.alertmanager.constant.service.AlertmanagerWebhookServiceLogConstant;
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;
import com.korur.oba.connect.alertmanager.dto.response.AlertmanagerWebhookResponse;
import com.korur.oba.connect.alertmanager.service.AlertmanagerNotificationHandler;
import com.korur.oba.connect.alertmanager.service.AlertmanagerWebhookService;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Hands each notification to the app's {@link AlertmanagerNotificationHandler}.
 * It opens no transaction of its own: the handler owns the unit of work, so a
 * failure rolls back exactly what the handler wrote.
 */
@Service
@RequiredArgsConstructor
public class AlertmanagerWebhookServiceImpl implements AlertmanagerWebhookService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(AlertmanagerWebhookServiceImpl.class);

    private final AlertmanagerNotificationHandler alertmanagerNotificationHandler;
    private final LoggingService loggingService;

    /**
     * Delegates one notification; see the interface for the contract.
     */
    @Override
    public AlertmanagerWebhookResponse processNotification(UUID organizationId, AlertmanagerWebhookRequest request) {
        loggingService.debug(
                LOG_PREFIX,
                LogLayer.SERVICE,
                AlertmanagerWebhookServiceLogConstant.NOTIFICATION_DELEGATED,
                request.alerts().size());

        return alertmanagerNotificationHandler.handle(organizationId, request);
    }
}
