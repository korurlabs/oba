package com.korur.oba.apps.otak.contact.constant.controller;

/**
 * Response message constants for contact controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ContactControllerResponseConstant {

    private ContactControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_CONTACT_LIST_RETRIEVED = "Contacts retrieved successfully";
    public static final String RESPONSE_CONTACT_CREATED = "Contact created successfully";
    public static final String RESPONSE_CONTACT_UPDATED = "Contact updated successfully";
    public static final String RESPONSE_CONTACT_DELETED = "Contact deleted successfully";
}
