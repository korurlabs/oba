package com.korur.oba.shrine.document.constant.template;

/**
 * Field names, limits and validation messages of the document template domain. The name
 * limit repeats the column size of shrine.document_templates, so a request that passes
 * validation always fits the table. The file rules shared with documents are in
 * {@link com.korur.oba.shrine.document.constant.common.DocumentFileConstant}.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentTemplateConstant {

    private DocumentTemplateConstant() {
        // Private constructor to prevent instantiation
    }

    // -------------Field names-----------//

    public static final String FIELD_ID = "id";
    public static final String FIELD_TEMPLATE_ID = "templateId";
    public static final String FIELD_ORGANIZATION_ID = "organizationId";
    public static final String FIELD_LEGAL_ENTITY_ID = "legalEntityId";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_LANGUAGE = "language";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_ENTITY_TYPE = "entityType";

    // -------------Limits-----------//

    public static final int NAME_MAX_LENGTH = 100;
    public static final int SEARCH_MAX_LENGTH = 100;

    // -------------Validation messages-----------//

    public static final String TYPE_REQUIRED_MESSAGE = "Type is required";
    public static final String LANGUAGE_REQUIRED_MESSAGE = "Language is required";
    public static final String NAME_REQUIRED_MESSAGE = "Name is required";
    public static final String NAME_SIZE_MESSAGE = "Name can not be longer than 100 characters";
    public static final String SEARCH_SIZE_MESSAGE = "Search can not be longer than 100 characters";
    public static final String SEARCH_BLANK_MESSAGE = "Search can not be empty; leave it out to list every template";
    public static final String ENTITY_TYPE_NOT_ATTACHABLE_MESSAGE = "A template can only be attached to a MAIL or a RELATION";
    public static final String TEMPLATE_WITHOUT_PDF_MESSAGE = "The template has no PDF file yet";
}
