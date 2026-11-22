package com.korur.oba.shrine.legalentity.constant.service;

/**
 * Log message constants for legal entity service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LegalEntityServiceLogConstant {

    private LegalEntityServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String LEGAL_ENTITY_CREATED = "Legal entity created: id={}, organizationId={}, createdBy={}";
    public static final String LEGAL_ENTITY_UPDATED = "Legal entity updated: id={}, organizationId={}";
    public static final String LEGAL_ENTITY_DELETED = "Legal entity deleted: id={}, organizationId={}";
    public static final String LISTING_LEGAL_ENTITIES = "Listing legal entities: organizationId={}";
    public static final String READING_LEGAL_ENTITY = "Reading legal entity: id={}, organizationId={}";
}
