package com.korur.oba.apps.otak.documenttemplate.constant;

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
     * Log message for an uploaded file that is not the HTML or PDF file its part names.
     */
    public static final String FILE_REFUSED = "Document file refused: part={}, contentType={}, size={}";

    /**
     * Log message for a template that can not be copied onto an attachment because it has no PDF yet.
     */
    public static final String TEMPLATE_NOT_USABLE = "Document template refused for an attachment: id={}, reason={}";

    /**
     * Log message for a copy onto an entity type that does not take document templates.
     */
    public static final String ENTITY_TYPE_NOT_ATTACHABLE =
            "Document template copy refused, entity type not attachable: templateId={}, entityType={}";
}
