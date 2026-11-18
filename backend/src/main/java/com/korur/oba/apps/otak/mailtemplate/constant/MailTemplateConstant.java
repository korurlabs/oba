package com.korur.oba.apps.otak.mailtemplate.constant;

/**
 * Field names, limits and validation messages of the mail template domain. The name and
 * subject limits repeat the column sizes of otak.mail_templates, and the body limit repeats
 * its check constraint, so a request that passes validation always fits the table.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class MailTemplateConstant {

    private MailTemplateConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_MAIL_TEMPLATE_ID = "mailTemplateId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_LANGUAGE = "language";
    public static final String FIELD_NAME = "name";

    // -------------Limits-----------//

    public static final int NAME_MAX_LENGTH = 100;
    public static final int SUBJECT_MAX_LENGTH = 255;
    public static final int BODY_MAX_LENGTH = 10000;
    public static final int SEARCH_MAX_LENGTH = 100;

    // -------------Validation messages-----------//

    public static final String LANGUAGE_REQUIRED_MESSAGE = "Language is required";
    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String NAME_SIZE_MESSAGE = "Name can not be longer than 100 characters";
    public static final String SUBJECT_REQUIRED_MESSAGE = "Subject is required";
    public static final String SUBJECT_SIZE_MESSAGE = "Subject can not be longer than 255 characters";
    public static final String BODY_REQUIRED_MESSAGE = "Body is required";
    public static final String BODY_SIZE_MESSAGE = "Body can not be longer than 10000 characters";
    public static final String SEARCH_SIZE_MESSAGE = "Search can not be longer than 100 characters";
    public static final String SEARCH_BLANK_MESSAGE =
            "Search can not be empty; leave it out to list every mail template";
}
