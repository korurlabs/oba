package com.korur.oba.connect.notification.dto;

import java.util.List;

public record Mail(
        String sender,
        List<String> recipients,
        String subject,
        String content,
        List<AttachmentEmail> attachments
) { }
