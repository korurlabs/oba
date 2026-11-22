package com.korur.oba.shrine.document.constant.service.template;

/**
 * Log message constants for document template service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentTemplateServiceLogConstant {

    private DocumentTemplateServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String TEMPLATE_CREATED = "Document template created: id={}, organizationId={}, createdBy={}";
    public static final String TEMPLATE_UPDATED = "Document template updated: id={}, organizationId={}, updatedBy={}";
    public static final String TEMPLATE_FILES_REPLACED = "Document template files replaced: id={}, organizationId={}, updatedBy={}";
    public static final String TEMPLATE_DELETED = "Document template deleted: id={}, organizationId={}";
    public static final String TEMPLATE_COPIED_TO_ATTACHMENT =
            "Document template copied to attachment: id={}, organizationId={}, entityType={}, entityId={}, "
                    + "attachmentId={}, userId={}";
    public static final String LISTING_TEMPLATES = "Listing document templates: organizationId={}, request={}";
    public static final String READING_TEMPLATE = "Reading document template: id={}, organizationId={}";

    public static final String FILE_SAVE_FAILED_CLEANUP =
            "Saving the document file URLs failed, deleting the new blobs: htmlUrl={}, pdfUrl={}";
}
