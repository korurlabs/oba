package com.korur.oba.apps.otak.contact.constant;

/**
 * Log message constants for contact domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ContactExceptionLogConstant {

    private ContactExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a contact the relation does not have, missing or of another organization.
     */
    public static final String CONTACT_NOT_FOUND = "Contact not found: id={}, relationId={}, organizationId={}";

    /**
     * Log message for a contact request with an email type but no email address.
     */
    public static final String CONTACT_EMAIL_TYPE_WITHOUT_EMAIL = "Contact refused, email type without email: emailType={}";

    /**
     * Log message for a contact request with a true main flag of the other kind.
     */
    public static final String CONTACT_MAIN_FLAG_NOT_FOR_KIND =
            "Contact refused, main flag of the other kind: kind={}, mainPersonContact={}, mainBusinessContact={}";

    /**
     * Log message for a same-kind update that sends false in the main flag of the contact's
     * kind while the contact is the main contact of that kind.
     */
    public static final String CONTACT_MAIN_FLAG_UNMARKED =
            "Contact refused, main flag false on the main contact of the kind: kind={}, contactId={}";
}
