package com.korur.oba.apps.otak.mailsender.constant;

/**
 * Field names, limits and validation messages of the mail sender domain. The mail address
 * limit repeats the column size of otak.mail_senders, which is the longest address the mail
 * standards allow, so a request that passes validation always fits the table.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailSenderConstant {

    private MailSenderConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_MAIL_SENDER_ID = "mailSenderId";
    public static final String FIELD_MAIL_ADDRESS = "mailAddress";

    // -------------Limits-----------//

    public static final int MAIL_ADDRESS_MAX_LENGTH = 254;

    // -------------Validation messages-----------//

    public static final String MAIL_ADDRESS_REQUIRED_MESSAGE = "Mail address is required";
    public static final String MAIL_ADDRESS_FORMAT_MESSAGE = "Mail address is not a valid mail address";
    public static final String TYPE_REQUIRED_MESSAGE = "Type is required";
    public static final String MAIL_ADDRESS_SIZE_MESSAGE = "Mail address can not be longer than 254 characters";
}
