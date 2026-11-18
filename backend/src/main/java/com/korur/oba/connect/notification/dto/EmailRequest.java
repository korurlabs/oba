package com.korur.oba.connect.notification.dto;

import java.util.List;
import java.util.Map;

import com.korur.oba.connect.notification.enums.EmailTemplateType;

/**
 * Initial Email request object. It will be used by internal services to invoke email notification.
 * @param sender - Nullable - The sender of the email, we will have a fallback email address in cases when this is null.
 * @param recipients - Not nullable - Receivers of this email.
 * @param emailTemplateType - Not nullable - The emailTemplateType determines which templates needs to be used
 * @param variables - nullable - Variables that will be enriched in our email templates.
 * @param attachments - nullable - List of AttachmentEmail, which can be used for sending attachments.
 */
public record EmailRequest(String sender,
                           List<String> recipients,
                           EmailTemplateType emailTemplateType,
                           Map<String, String> variables,
                           List<AttachmentEmail> attachments) {
}
