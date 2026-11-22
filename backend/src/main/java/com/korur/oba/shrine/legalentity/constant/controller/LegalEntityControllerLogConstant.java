package com.korur.oba.shrine.legalentity.constant.controller;

/**
 * Log message constants for legal entity controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LegalEntityControllerLogConstant {

    private LegalEntityControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String LEGAL_ENTITY_LIST_REQUEST = "Received request to list legal entities";
    public static final String LEGAL_ENTITY_LIST_COMPLETED = "List legal entities request completed";

    public static final String LEGAL_ENTITY_GET_REQUEST = "Received request to get legal entity";
    public static final String LEGAL_ENTITY_GET_COMPLETED = "Get legal entity request completed";

    public static final String LEGAL_ENTITY_CREATE_REQUEST = "Received request to create legal entity";
    public static final String LEGAL_ENTITY_CREATE_COMPLETED = "Create legal entity request completed";

    public static final String LEGAL_ENTITY_UPDATE_REQUEST = "Received request to update legal entity";
    public static final String LEGAL_ENTITY_UPDATE_COMPLETED = "Update legal entity request completed";

    public static final String LEGAL_ENTITY_DELETE_REQUEST = "Received request to delete legal entity";
    public static final String LEGAL_ENTITY_DELETE_COMPLETED = "Delete legal entity request completed";
}
