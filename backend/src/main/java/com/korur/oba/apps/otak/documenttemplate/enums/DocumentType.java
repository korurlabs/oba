package com.korur.oba.apps.otak.documenttemplate.enums;

/**
 * Kind of content a document template holds. Mapped by name to the
 * PostgreSQL enum otak_document_type_enum, in its order, so a new constant needs a
 * migration that adds the same value to that type.
 *
 * Every type holds the same content, an HTML file and the PDF rendered from it, whose PDF
 * is what a mail attaches. The types differ only in meaning: the label shown to the user
 * and the list filter. The type may therefore change on update. A mail is not a document
 * type: mails and mail templates are their own entities.
 *
 * Used by {@link com.korur.oba.apps.otak.documenttemplate.entity.DocumentTemplate}.
 *
 * @see DocumentLanguage
 */
public enum DocumentType {

    /**
     * A commercial proposal.
     */
    PROPOSAL,

    /**
     * A non-disclosure agreement.
     */
    NDA,

    /**
     * General terms and conditions.
     */
    TERMS,

    /**
     * A report, such as the result of a security check.
     */
    REPORT,

    /**
     * A one-page description of a service.
     */
    SERVICE_SHEET,

    /**
     * A presentation.
     */
    PRESENTATION,

    /**
     * Any other document.
     */
    OTHER
}
