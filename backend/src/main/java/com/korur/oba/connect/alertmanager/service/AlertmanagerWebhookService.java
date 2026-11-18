package com.korur.oba.connect.alertmanager.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;
import com.korur.oba.connect.alertmanager.dto.response.AlertmanagerWebhookResponse;

/**
 * Intake of Alertmanager notifications: passes each authenticated notification to
 * the app that consumes alerts.
 */
public interface AlertmanagerWebhookService {

    /**
     * Processes one notification by handing it to the one
     * {@link AlertmanagerNotificationHandler} and returning its counts unchanged.
     *
     * @param organizationId the organization bound to the token that authenticated the call
     * @param request        the notification
     * @return the created and ignored counts
     */
    AlertmanagerWebhookResponse processNotification(UUID organizationId, AlertmanagerWebhookRequest request);
}
