package com.korur.oba.apps.otak.mail.constant;

/**
 * Field names, limits and validation messages of the mail domain. The subject and body
 * limits repeat those of otak.mails, so a request that passes validation always fits the
 * table.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailConstant {

    private MailConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_MAIL_ID = "mailId";

    // -------------Limits-----------//

    public static final int SUBJECT_MAX_LENGTH = 255;
    public static final int BODY_MAX_LENGTH = 10000;

    // -------------Validation messages-----------//

    public static final String SUBJECT_REQUIRED_MESSAGE = "Subject is required";
    public static final String SUBJECT_SIZE_MESSAGE = "Subject can not be longer than 255 characters";
    public static final String BODY_REQUIRED_MESSAGE = "Body is required";
    public static final String BODY_SIZE_MESSAGE = "Body can not be longer than 10000 characters";
    public static final String LANGUAGE_REQUIRED_MESSAGE = "Language is required";
}
