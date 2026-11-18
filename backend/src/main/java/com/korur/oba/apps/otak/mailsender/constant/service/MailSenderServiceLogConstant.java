package com.korur.oba.apps.otak.mailsender.constant.service;

/**
 * Log message constants for mail sender service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailSenderServiceLogConstant {

    private MailSenderServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String MAIL_SENDER_CREATED = "Mail sender added: id={}, organizationId={}, addedBy={}";
    public static final String MAIL_SENDER_DELETED = "Mail sender deleted: id={}, organizationId={}";
    public static final String LISTING_MAIL_SENDERS = "Listing mail senders: organizationId={}";
}
