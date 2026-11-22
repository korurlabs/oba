package com.korur.oba.shrine.document.constant.controller.document;

/**
 * Log message constants for document controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentControllerLogConstant {

    private DocumentControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DOCUMENT_LIST_REQUEST = "Received request to list documents";
    public static final String DOCUMENT_LIST_COMPLETED = "List documents request completed";

    public static final String DOCUMENT_CREATE_REQUEST = "Received request to create document";
    public static final String DOCUMENT_CREATE_COMPLETED = "Create document request completed";

    public static final String DOCUMENT_GET_REQUEST = "Received request to get document";
    public static final String DOCUMENT_GET_COMPLETED = "Get document request completed";

    public static final String DOCUMENT_UPDATE_REQUEST = "Received request to update document";
    public static final String DOCUMENT_UPDATE_COMPLETED = "Update document request completed";

    public static final String DOCUMENT_FILES_REQUEST = "Received request to replace document files";
    public static final String DOCUMENT_FILES_COMPLETED = "Replace document files request completed";

    public static final String DOCUMENT_DELETE_REQUEST = "Received request to delete document";
    public static final String DOCUMENT_DELETE_COMPLETED = "Delete document request completed";

    public static final String DOCUMENT_ATTACH_REQUEST = "Received request to attach document";
    public static final String DOCUMENT_ATTACH_COMPLETED = "Attach document request completed";
}
