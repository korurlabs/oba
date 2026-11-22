package com.korur.oba.shrine.document.constant.service.document;

/**
 * Log message constants for document service operations.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentServiceLogConstant {

    private DocumentServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DOCUMENT_CREATED =
            "Document created: id={}, organizationId={}, templateId={}, createdBy={}";
    public static final String DOCUMENT_UPDATED = "Document updated: id={}, organizationId={}, updatedBy={}";
    public static final String DOCUMENT_FILES_REPLACED =
            "Document files replaced: id={}, organizationId={}, updatedBy={}";
    public static final String DOCUMENT_DELETED = "Document deleted: id={}, organizationId={}";
    public static final String DOCUMENT_ATTACHED =
            "Document attached: id={}, organizationId={}, entityType={}, entityId={}, attachmentId={}, userId={}";
    public static final String DOCUMENTS_DELETED_BY_ENTITY = "Documents deleted with their owner: count={}, "
            + "entityType={}, entityId={}";
    public static final String LISTING_DOCUMENTS = "Listing documents: organizationId={}, request={}";
    public static final String READING_DOCUMENT = "Reading document: id={}, organizationId={}";

    public static final String FILE_SAVE_FAILED_CLEANUP =
            "Saving the document file URLs failed, deleting the new blobs: htmlUrl={}, pdfUrl={}";
}
