package com.korur.oba.shrine.document.constant.document;

/**
 * Log message constants for document domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentExceptionLogConstant {

    private DocumentExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String DOCUMENT_NOT_FOUND = "Document not found: id={}, organizationId={}";
    public static final String DOCUMENT_WITHOUT_PDF = "Document refused for an attachment, no PDF: id={}";
    public static final String ENTITY_TYPE_NOT_ATTACHABLE =
            "Document attach refused, entity type not attachable: documentId={}, entityType={}";
    public static final String MAIL_OF_OTHER_RELATION =
            "Document attach refused, mail of another relation: documentId={}, mailId={}, documentRelationId={}, "
                    + "mailRelationId={}";
}
