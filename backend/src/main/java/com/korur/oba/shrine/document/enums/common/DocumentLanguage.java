package com.korur.oba.shrine.document.enums.common;

/**
 * Language a document template, a mail or a mail template is written in. Mapped by name to
 * the PostgreSQL enum shrine_document_language_enum, in its order, so a new constant needs a
 * migration that adds the same value to that type. It lives in shrine because templates are
 * organization-wide while Otak's mails and mail templates use the same set of languages.
 *
 * Together with the name it identifies a template within an organization: the same name
 * may exist once per language, which is how one template is kept in several translations
 * (see {@link com.korur.oba.shrine.document.validator.template.DocumentTemplateValidator#ensureNameFree}).
 *
 * Used by {@link com.korur.oba.shrine.document.entity.template.DocumentTemplate}, its list
 * filter, and Otak's mails and mail templates. A mail written by hand always carries its
 * language, which the client must send; the server has no default for it, so a client that
 * offers one applies it itself.
 *
 * @see DocumentType
 */
public enum DocumentLanguage {

    /**
     * Dutch.
     */
    NL,

    /**
     * Turkish.
     */
    TR,

    /**
     * English.
     */
    EN
}
