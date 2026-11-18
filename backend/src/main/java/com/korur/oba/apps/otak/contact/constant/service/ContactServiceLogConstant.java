package com.korur.oba.apps.otak.contact.constant.service;

/**
 * Log message constants for contact service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ContactServiceLogConstant {

    private ContactServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CONTACT_CREATED = "Contact created: id={}, relationId={}, organizationId={}, createdBy={}";
    public static final String CONTACT_UPDATED = "Contact updated: id={}, relationId={}, organizationId={}";
    public static final String CONTACT_DELETED = "Contact deleted: id={}, relationId={}, organizationId={}";
    public static final String MAIN_CONTACT_CHANGED =
            "Main contact changed: kind={}, id={}, relationId={}, organizationId={}";
    public static final String MAIN_CONTACT_PROMOTED =
            "Main contact promoted after delete or kind change: kind={}, id={}, relationId={}, organizationId={}";
    public static final String LISTING_CONTACTS = "Listing contacts: relationId={}, organizationId={}";
}
