package com.korur.oba.shrine.legalentity.constant.controller;

/**
 * Response message constants for legal entity controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LegalEntityControllerResponseConstant {

    private LegalEntityControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_LEGAL_ENTITY_LIST_RETRIEVED = "Legal entities retrieved successfully";
    public static final String RESPONSE_LEGAL_ENTITY_RETRIEVED = "Legal entity retrieved successfully";
    public static final String RESPONSE_LEGAL_ENTITY_CREATED = "Legal entity created successfully";
    public static final String RESPONSE_LEGAL_ENTITY_UPDATED = "Legal entity updated successfully";
    public static final String RESPONSE_LEGAL_ENTITY_DELETED = "Legal entity deleted successfully";
}
