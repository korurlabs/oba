package com.korur.oba.apps.otak.contact.constant.controller;

/**
 * Log message constants for contact controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ContactControllerLogConstant {

    private ContactControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String CONTACT_LIST_REQUEST = "Received request to list contacts";
    public static final String CONTACT_LIST_COMPLETED = "List contacts request completed";

    public static final String CONTACT_CREATE_REQUEST = "Received request to create contact";
    public static final String CONTACT_CREATE_COMPLETED = "Create contact request completed";

    public static final String CONTACT_UPDATE_REQUEST = "Received request to update contact";
    public static final String CONTACT_UPDATE_COMPLETED = "Update contact request completed";

    public static final String CONTACT_DELETE_REQUEST = "Received request to delete contact";
    public static final String CONTACT_DELETE_COMPLETED = "Delete contact request completed";
}
