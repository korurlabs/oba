package com.korur.oba.shrine.document.constant.common;

/**
 * Log message constants of the document file storage: uploads, refused files, unreadable
 * parts and blobs that could not be deleted.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentFileLogConstant {

    private DocumentFileLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String FILE_UPLOADED = "Document file uploaded: blobName={}, size={}";
    public static final String FILE_READ_FAILED = "Document file could not be read from the request: part={}";
    public static final String FILE_BLOB_DELETE_FAILED = "Document blob could not be deleted and is orphaned: url={}";

    /**
     * Log message for an uploaded file that is not the HTML or PDF file its part names.
     */
    public static final String FILE_REFUSED = "Document file refused: part={}, contentType={}, size={}";
}
