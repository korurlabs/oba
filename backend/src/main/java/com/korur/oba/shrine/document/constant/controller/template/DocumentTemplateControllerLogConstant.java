package com.korur.oba.shrine.document.constant.controller.template;

/**
 * Log message constants for document template controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentTemplateControllerLogConstant {

    private DocumentTemplateControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String TEMPLATE_LIST_REQUEST = "Received request to list document templates";
    public static final String TEMPLATE_LIST_COMPLETED = "List document templates request completed";

    public static final String TEMPLATE_CREATE_REQUEST = "Received request to create document template";
    public static final String TEMPLATE_CREATE_COMPLETED = "Create document template request completed";

    public static final String TEMPLATE_GET_REQUEST = "Received request to get document template";
    public static final String TEMPLATE_GET_COMPLETED = "Get document template request completed";

    public static final String TEMPLATE_UPDATE_REQUEST = "Received request to update document template";
    public static final String TEMPLATE_UPDATE_COMPLETED = "Update document template request completed";

    public static final String TEMPLATE_FILES_REQUEST = "Received request to replace document template files";
    public static final String TEMPLATE_FILES_COMPLETED = "Replace document template files request completed";

    public static final String TEMPLATE_DELETE_REQUEST = "Received request to delete document template";
    public static final String TEMPLATE_DELETE_COMPLETED = "Delete document template request completed";
}
