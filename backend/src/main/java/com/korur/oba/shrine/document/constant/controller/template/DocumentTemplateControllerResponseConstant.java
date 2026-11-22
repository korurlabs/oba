package com.korur.oba.shrine.document.constant.controller.template;

/**
 * Response message constants for document template controller operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentTemplateControllerResponseConstant {

    private DocumentTemplateControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_TEMPLATE_LIST_RETRIEVED = "Document templates retrieved successfully";
    public static final String RESPONSE_TEMPLATE_CREATED = "Document template created successfully";
    public static final String RESPONSE_TEMPLATE_RETRIEVED = "Document template retrieved successfully";
    public static final String RESPONSE_TEMPLATE_UPDATED = "Document template updated successfully";
    public static final String RESPONSE_TEMPLATE_FILES_REPLACED = "Document template files replaced successfully";
    public static final String RESPONSE_TEMPLATE_DELETED = "Document template deleted successfully";
}
