package com.korur.oba.shrine.document.constant.document;

/**
 * Field names, limits and validation messages of the document domain. The title limit
 * repeats the column size of shrine.documents, so a request that passes validation fits the
 * table. The file rules shared with templates are in
 * {@link com.korur.oba.shrine.document.constant.common.DocumentFileConstant}.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentConstant {

    private DocumentConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_DOCUMENT_ID = "documentId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_LEGAL_ENTITY_ID = "legalEntityId";
    public static final String FIELD_ENTITY_TYPE = "entityType";
    public static final String FIELD_ENTITY_ID = "entityId";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_LANGUAGE = "language";
    public static final String FIELD_UPDATED_AT = "updatedAt";

    // -------------Limits-----------//

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int SEARCH_MAX_LENGTH = 100;

    // -------------Display-----------//

    public static final String NAME_SEPARATOR = " ";

    // -------------Validation messages-----------//

    public static final String ENTITY_TYPE_REQUIRED_MESSAGE = "Entity type is required";
    public static final String ENTITY_ID_REQUIRED_MESSAGE = "Entity id is required";
    public static final String LEGAL_ENTITY_REQUIRED_MESSAGE = "Legal entity is required";
    public static final String TYPE_REQUIRED_MESSAGE = "Type is required";
    public static final String LANGUAGE_REQUIRED_MESSAGE = "Language is required";
    public static final String TITLE_REQUIRED_MESSAGE = "Title is required";
    public static final String TITLE_SIZE_MESSAGE = "Title can not be longer than 200 characters";
    public static final String SEARCH_SIZE_MESSAGE = "Search can not be longer than 100 characters";
    public static final String SEARCH_BLANK_MESSAGE = "Search can not be empty; leave it out to list every document";
    public static final String ENTITY_TYPE_NOT_ATTACHABLE_MESSAGE = "A document can only be attached to a MAIL";
    public static final String DOCUMENT_WITHOUT_PDF_MESSAGE = "The document has no PDF file yet";
    public static final String MAIL_OF_OTHER_RELATION_MESSAGE = "The mail belongs to another relation";
}
