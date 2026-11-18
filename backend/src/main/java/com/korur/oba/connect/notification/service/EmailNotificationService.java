package com.korur.oba.connect.notification.service;

import com.korur.oba.connect.notification.dto.EmailRequest;


public interface EmailNotificationService {

    void sendEmail(EmailRequest emailRequest);
}
