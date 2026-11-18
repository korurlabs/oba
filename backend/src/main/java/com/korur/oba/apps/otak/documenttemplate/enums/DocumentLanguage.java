package com.korur.oba.apps.otak.documenttemplate.enums;

/**
 * Language a document template is written in. Mapped by name to the
 * PostgreSQL enum otak_document_language_enum, in its order, so a new constant needs a
 * migration that adds the same value to that type.
 *
 * Together with the name it identifies a template within an organization: the same name
 * may exist once per language, which is how one template is kept in several translations
 * (see {@link com.korur.oba.apps.otak.documenttemplate.validator.DocumentTemplateValidator#ensureNameFree}).
 *
 * Used by {@link com.korur.oba.apps.otak.documenttemplate.entity.DocumentTemplate}, its list
 * filter, and the outreach mail. A mail written by hand always carries its language, which the client
 * must send ({@link com.korur.oba.apps.otak.mail.dto.request.MailRequest});
 * the server has no default for it, so a client that offers one applies it itself.
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
