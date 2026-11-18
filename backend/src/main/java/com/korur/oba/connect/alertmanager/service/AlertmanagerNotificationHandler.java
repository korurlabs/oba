package com.korur.oba.connect.alertmanager.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.connect.alertmanager.dto.request.AlertmanagerWebhookRequest;
import com.korur.oba.connect.alertmanager.dto.response.AlertmanagerWebhookResponse;

/**
 * What an app does with an Alertmanager notification.
 *
 * The webhook knows only the Alertmanager protocol: the token, the payload and
 * the counts it answers with. It does not know which app turns alerts into work,
 * so that app implements this interface as a Spring bean and
 * {@link AlertmanagerWebhookService} hands every authenticated notification to it.
 * Exactly one implementation must exist; with none or two the application does
 * not start, so no notification is ever dropped or handled twice.
 *
 * The handler runs the whole notification as one unit: when it throws, nothing of
 * the notification may stay written, because Alertmanager delivers the group again
 * and the retry must not create duplicates.
 */
public interface AlertmanagerNotificationHandler {

    /**
     * Handles one authenticated notification within one organization. The
     * organization comes from the configuration bound to the token, never from
     * the notification, so its alerts may only reach that organization's data.
     *
     * @param organizationId the organization bound to the token that authenticated the call
     * @param request        the notification, already validated
     * @return the created and ignored counts the webhook answers with
     */
    AlertmanagerWebhookResponse handle(UUID organizationId, AlertmanagerWebhookRequest request);
}
