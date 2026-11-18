package com.korur.oba.connect.notification.handler;

// 1. Framework-level
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

// 2. Third-party
import lombok.RequiredArgsConstructor;

import com.korur.oba.connect.notification.constant.handler.NotificationOutboxHandlerLogConstant;
import com.korur.oba.connect.notification.dto.EmailRequest;
import com.korur.oba.connect.notification.exception.ExceptionDomainNotificationError;
import com.korur.oba.connect.notification.service.EmailNotificationService;
import com.korur.oba.connect.outbox.entity.OutboxEvent;
import com.korur.oba.connect.outbox.enums.ConnectOutboxSystem;
import com.korur.oba.connect.outbox.handler.OutboxEventHandler;
// 4. Internal
import com.korur.oba.dojo.exception.external.InternalProcessException;
import com.korur.oba.dojo.exception.external.ThirdPartyException;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.JsonUtil;

/**
 * Handles outbox events targeted at the NOTIFICATION system.
 * Dispatches email notifications based on event type.
 */
@Service
@RequiredArgsConstructor
public class NotificationOutboxEventHandler implements OutboxEventHandler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(NotificationOutboxEventHandler.class);

    private final LoggingService loggingService;
    private final EmailNotificationService emailNotificationService;

    @Override
    public boolean supports(ConnectOutboxSystem connectOutboxSystem) {
        return ConnectOutboxSystem.NOTIFICATION.equals(connectOutboxSystem);
    }

    @Override
    public void handle(OutboxEvent event) {
        EmailRequest emailRequest = JsonUtil.fromJson(event.getPayload(), EmailRequest.class);
        sendEmail(emailRequest);
    }

    private void sendEmail(EmailRequest emailRequest) {
        loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                NotificationOutboxHandlerLogConstant.OUTBOX_HANDLER_SENDING_EMAIL,
                emailRequest.emailTemplateType(), emailRequest.recipients());
        try {
            emailNotificationService.sendEmail(emailRequest);
        } catch (InternalProcessException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new InternalProcessException(ExceptionDomainNotificationError.INT_EMAIL_PERSISTENCE_FAILED, e);
        } catch (ApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new ThirdPartyException(ExceptionDomainNotificationError.UPS_EMAIL_SEND_FAILED, e);
        }
        loggingService.info(LOG_PREFIX, LogLayer.HANDLER,
                NotificationOutboxHandlerLogConstant.OUTBOX_HANDLER_EMAIL_SENT,
                emailRequest.emailTemplateType(), emailRequest.recipients());
    }
}
