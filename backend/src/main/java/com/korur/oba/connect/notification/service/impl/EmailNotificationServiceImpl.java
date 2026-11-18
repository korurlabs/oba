package com.korur.oba.connect.notification.service.impl;

// 1. Framework-level
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

// 2. Third-party
import org.apache.commons.lang3.StringUtils;

// 3. Java SDK
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.korur.oba.connect.notification.client.MsGraphClient;
import com.korur.oba.connect.notification.config.EmailConfiguration;
import com.korur.oba.connect.notification.constant.NotificationConstant;
import com.korur.oba.connect.notification.constant.service.NotificationServiceLogConstant;
import com.korur.oba.connect.notification.dto.EmailRequest;
import com.korur.oba.connect.notification.dto.Mail;
import com.korur.oba.connect.notification.entity.EmailNotification;
import com.korur.oba.connect.notification.repository.EmailNotificationRepository;
import com.korur.oba.connect.notification.service.EmailNotificationService;
import com.korur.oba.connect.notification.utils.EmailTemplateProcessor;
// 4. Internal
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

@Service
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(EmailNotificationServiceImpl.class);

    private final EmailNotificationRepository emailNotificationRepository;
    private final EmailTemplateProcessor emailTemplateProcessor;
    private final EmailConfiguration emailConfiguration;
    private final MsGraphClient msGraphClient;
    private final LoggingService loggingService;

    public EmailNotificationServiceImpl(
            EmailNotificationRepository emailNotificationRepository,
            EmailTemplateProcessor emailTemplateProcessor,
            EmailConfiguration emailConfiguration,
            @Qualifier("emailMsGraphClient") MsGraphClient msGraphClient,
            LoggingService loggingService) {
        this.emailNotificationRepository = emailNotificationRepository;
        this.emailTemplateProcessor = emailTemplateProcessor;
        this.emailConfiguration = emailConfiguration;
        this.msGraphClient = msGraphClient;
        this.loggingService = loggingService;
    }

    @Async("notificationExecutor")
    @Override
    public void sendEmail(EmailRequest emailRequest) {
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                NotificationServiceLogConstant.SENDING_EMAIL,
                emailRequest.emailTemplateType(), emailRequest.recipients());

        Map<String, String> variables = new HashMap<>();
        if (!Objects.isNull(emailRequest.variables())) {
            variables.putAll(emailRequest.variables());
        }
        variables.put(NotificationConstant.SENSEY_LOGO_VARIABLE, emailConfiguration.getSenseyLogoUrl());

        String templateName = emailRequest.emailTemplateType().getTemplateName();
        String htmlContent = emailTemplateProcessor.process(templateName, variables);
        String subject = NotificationConstant.EMAIL_SUBJECT_PREFIX
                + emailRequest.emailTemplateType().name().replace("_", " ");

        String sender = StringUtils.isNotEmpty(emailRequest.sender()) ? emailRequest.sender()
                : emailConfiguration.getDefaultSender();
        Mail mail = new Mail(
                sender,
                emailRequest.recipients(),
                subject,
                htmlContent,
                emailRequest.attachments());

        boolean sent = false;
        try {
            msGraphClient.sendEmail(mail);
            sent = true;
            loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                    NotificationServiceLogConstant.EMAIL_SENT_SUCCESSFULLY,
                    emailRequest.emailTemplateType(), emailRequest.recipients());
        } catch (Exception e) {
            loggingService.error(LOG_PREFIX, LogLayer.SERVICE,
                    NotificationServiceLogConstant.EMAIL_SEND_FAILED,
                    e, emailRequest.emailTemplateType(), emailRequest.recipients());
        }

        EmailNotification emailNotification = EmailNotification.builder()
                .sender(sender)
                .emailTemplateType(emailRequest.emailTemplateType())
                .send(sent)
                .recipients(emailRequest.recipients())
                .mail(mail)
                .build();

        emailNotificationRepository.save(emailNotification);
        loggingService.info(LOG_PREFIX, LogLayer.SERVICE,
                NotificationServiceLogConstant.EMAIL_PERSISTED,
                emailRequest.emailTemplateType(), sent);
    }
}
