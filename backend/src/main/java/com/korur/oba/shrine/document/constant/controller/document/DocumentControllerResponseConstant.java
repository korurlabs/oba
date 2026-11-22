package com.korur.oba.shrine.document.constant.controller.document;

/**
 * Response message constants for document controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentControllerResponseConstant {

    private DocumentControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_DOCUMENT_LIST_RETRIEVED = "Documents retrieved successfully";
    public static final String RESPONSE_DOCUMENT_CREATED = "Document created successfully";
    public static final String RESPONSE_DOCUMENT_RETRIEVED = "Document retrieved successfully";
    public static final String RESPONSE_DOCUMENT_UPDATED = "Document updated successfully";
    public static final String RESPONSE_DOCUMENT_FILES_REPLACED = "Document files replaced successfully";
    public static final String RESPONSE_DOCUMENT_DELETED = "Document deleted successfully";
    public static final String RESPONSE_DOCUMENT_ATTACHED = "Document attached successfully";
}
