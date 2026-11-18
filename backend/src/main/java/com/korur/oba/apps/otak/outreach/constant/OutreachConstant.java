package com.korur.oba.apps.otak.outreach.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits and validation messages of the outreach domain. The title limit
 * repeats the column size of otak.outreaches, so a request that passes validation always
 * fits the table. The limits and messages of the mail itself are in
 * {@link com.korur.oba.apps.otak.mail.constant.MailConstant}.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class OutreachConstant {

    private OutreachConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_OUTREACH_ID = "outreachId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_RELATION_ID = "relationId";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_MAIL = "mail";
    public static final String FIELD_MAIL_TEMPLATE_ID = "mailTemplateId";
    public static final String FIELD_CONTACT_ID = "contactId";
    public static final String FIELD_MAIL_SENDER_ID = "mailSenderId";
    public static final String FIELD_DOCUMENT_TEMPLATE_IDS = "documentTemplateIds";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_SIZE_BYTES = "sizeBytes";
    public static final String FIELD_MAX_SIZE_BYTES = "maxSizeBytes";

    // -------------Limits-----------//

    public static final int TITLE_MAX_LENGTH = 200;

    /**
     * Largest total size of the attachments sent with one outreach. Graph refuses a sendMail
     * request over about 4 MB, and base64 makes an attachment a third larger.
     */
    public static final long ATTACHMENTS_MAX_BYTES = 3L * 1024 * 1024;

    // -------------Send-----------//

    /**
     * Line breaks of a plain-text body, in any of the three conventions.
     */
    public static final String LINE_BREAK_PATTERN = "\\r\\n|\\r|\\n";

    /**
     * HTML line break that replaces a line break of the escaped plain-text body.
     */
    public static final String HTML_LINE_BREAK = "<br>";

    // -------------Validation messages-----------//

    public static final String RELATION_REQUIRED_MESSAGE = "Relation is required";
    public static final String CONTACT_REQUIRED_MESSAGE = "Contact is required";
    public static final String TITLE_REQUIRED_MESSAGE = "Title is required";
    public static final String TITLE_SIZE_MESSAGE = "Title can not be longer than 200 characters";
    public static final String MAIL_REQUIRED_MESSAGE = "Mail is required";
    public static final String DOCUMENT_TEMPLATE_ID_REQUIRED_MESSAGE = "Document template id can not be null";
    public static final String MAIL_SENDER_REQUIRED_MESSAGE = "Mail sender is required";
    public static final String ONE_MAIL_SOURCE_MESSAGE = "Give either a mail template or a mail, not both and not neither";
    public static final String TYPE_REQUIRED_MESSAGE = "Type is required";
    public static final String MAIL_TEMPLATE_NOT_FOR_TYPE_MESSAGE = "Only a mail outreach takes a mail template";
    public static final String MAIL_NOT_FOR_TYPE_MESSAGE = "Only a mail outreach takes a mail";
    public static final String DOCUMENT_TEMPLATES_NOT_FOR_TYPE_MESSAGE = "Only a mail outreach takes document templates";
}
