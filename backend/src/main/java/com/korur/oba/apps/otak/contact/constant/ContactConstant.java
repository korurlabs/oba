package com.korur.oba.apps.otak.contact.constant;

/**
 * Field names, limits and validation messages of the contact domain. The limits
 * repeat the column sizes of otak.contacts, so a request that passes validation
 * always fits the table.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ContactConstant {

    private ContactConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_CONTACT_ID = "contactId";
    public static final String FIELD_RELATION_ID = "relationId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_EMAIL_TYPE = "emailType";
    public static final String FIELD_KIND = "kind";
    public static final String FIELD_MAIN_PERSON_CONTACT = "mainPersonContact";
    public static final String FIELD_MAIN_BUSINESS_CONTACT = "mainBusinessContact";

    // -------------Limits-----------//

    public static final int NAME_MAX_LENGTH = 200;
    public static final int ROLE_MAX_LENGTH = 100;
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int PHONE_MAX_LENGTH = 50;
    public static final int LINKEDIN_URL_MAX_LENGTH = 500;
    public static final int SOURCE_URL_MAX_LENGTH = 500;

    // -------------Patterns-----------//

    /**
     * An absolute http or https URL without spaces.
     */
    public static final String LINKEDIN_URL_PATTERN = "^https?://[^\\s/?#]+\\.[^\\s]+$";

    /**
     * An absolute http or https URL without spaces.
     */
    public static final String SOURCE_URL_PATTERN = "^https?://[^\\s/?#]+\\.[^\\s]+$";

    // -------------Validation messages-----------//

    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String KIND_REQUIRED_MESSAGE = "Kind is required";
    public static final String DECISION_MAKER_REQUIRED_MESSAGE = "Decision maker is required";
    public static final String MAIN_PERSON_CONTACT_REQUIRED_MESSAGE = "Main person contact is required";
    public static final String MAIN_BUSINESS_CONTACT_REQUIRED_MESSAGE = "Main business contact is required";
    public static final String MAIN_CONTACT_UNMARK_MESSAGE = "Mark another contact as main instead";
    public static final String ROLE_BLANK_MESSAGE = "Role can not be empty; send null for none";
    public static final String EMAIL_BLANK_MESSAGE = "Email can not be empty; send null for none";
    public static final String PHONE_BLANK_MESSAGE = "Phone can not be empty; send null for none";
    public static final String LINKEDIN_URL_BLANK_MESSAGE = "LinkedIn URL can not be empty; send null for none";
    public static final String SOURCE_URL_BLANK_MESSAGE = "Source URL can not be empty; send null for none";
    public static final String NAME_SIZE_MESSAGE = "Name can not be longer than 200 characters";
    public static final String ROLE_SIZE_MESSAGE = "Role can not be longer than 100 characters";
    public static final String EMAIL_SIZE_MESSAGE = "Email can not be longer than 254 characters";
    public static final String EMAIL_FORMAT_MESSAGE = "Email must be a valid email address";
    public static final String PHONE_SIZE_MESSAGE = "Phone can not be longer than 50 characters";
    public static final String LINKEDIN_URL_SIZE_MESSAGE = "LinkedIn URL can not be longer than 500 characters";
    public static final String LINKEDIN_URL_PATTERN_MESSAGE = "LinkedIn URL must be a full http or https address";
    public static final String SOURCE_URL_SIZE_MESSAGE = "Source URL can not be longer than 500 characters";
    public static final String SOURCE_URL_PATTERN_MESSAGE = "Source URL must be a full http or https address";
}
