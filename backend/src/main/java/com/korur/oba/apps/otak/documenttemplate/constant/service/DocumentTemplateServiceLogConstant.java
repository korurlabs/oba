package com.korur.oba.apps.otak.documenttemplate.constant.service;

/**
 * Log message constants for document template service operations and its file storage.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentTemplateServiceLogConstant {

    private DocumentTemplateServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String TEMPLATE_CREATED = "Document template created: id={}, organizationId={}, createdBy={}";
    public static final String TEMPLATE_UPDATED = "Document template updated: id={}, organizationId={}";
    public static final String TEMPLATE_FILES_REPLACED = "Document template files replaced: id={}, organizationId={}";
    public static final String TEMPLATE_DELETED = "Document template deleted: id={}, organizationId={}";
    public static final String TEMPLATE_COPIED_TO_ATTACHMENT =
            "Document template copied to attachment: id={}, organizationId={}, entityType={}, entityId={}, "
                    + "attachmentId={}, userId={}";
    public static final String LISTING_TEMPLATES = "Listing document templates: organizationId={}, request={}";
    public static final String READING_TEMPLATE = "Reading document template: id={}, organizationId={}";

    public static final String FILE_UPLOADED = "Document file uploaded: blobName={}, size={}";
    public static final String FILE_READ_FAILED = "Document file could not be read from the request: part={}";
    public static final String FILE_SAVE_FAILED_CLEANUP =
            "Saving the document file URLs failed, deleting the new blobs: htmlUrl={}, pdfUrl={}";
    public static final String FILE_BLOB_DELETE_FAILED = "Document blob could not be deleted and is orphaned: url={}";
}
