package com.korur.oba.shrine.document.constant.template;

/**
 * Log message constants for document template domain exceptions.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class DocumentTemplateExceptionLogConstant {

    private DocumentTemplateExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for a template that the organization does not have, missing or of another organization.
     */
    public static final String TEMPLATE_NOT_FOUND = "Document template not found: id={}, organizationId={}";

    /**
     * Log message for a name and language another template of the organization already has, case ignored.
     */
    public static final String TEMPLATE_NAME_TAKEN =
            "Document template name refused, already taken: organizationId={}, name={}, language={}";


    /**
     * Log message for a template that can not be copied onto an attachment because it has no PDF yet.
     */
    public static final String TEMPLATE_NOT_USABLE = "Document template refused for an attachment: id={}, reason={}";

    /**
     * Log message for a template that can not seed a document because it is bound to another legal entity.
     */
    public static final String TEMPLATE_OF_OTHER_LEGAL_ENTITY =
            "Document template refused for a document of another legal entity: id={}, templateLegalEntityId={}, "
                    + "documentLegalEntityId={}";

    /**
     * Log message for a copy onto an entity type that does not take document templates.
     */
    public static final String ENTITY_TYPE_NOT_ATTACHABLE =
            "Document template copy refused, entity type not attachable: templateId={}, entityType={}";
}
