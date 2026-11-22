package com.korur.oba.shrine.legalentity.constant;

/**
 * Log message constants for legal entity domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LegalEntityExceptionLogConstant {

    private LegalEntityExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a legal entity that the organization does not have, missing or of another organization.
     */
    public static final String LEGAL_ENTITY_NOT_FOUND = "Legal entity not found: id={}, organizationId={}";

    /**
     * Log message for a short name another legal entity of the organization already has, case ignored.
     */
    public static final String LEGAL_ENTITY_SHORT_NAME_TAKEN =
            "Legal entity short name refused, already taken: organizationId={}, shortName={}";
}
