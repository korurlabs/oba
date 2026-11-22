package com.korur.oba.apps.otak.mailtemplate.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.dojo.entity.BaseEntity;
import com.korur.oba.shrine.document.enums.common.DocumentLanguage;

/**
 * Entity representing a mail template, a row of otak.mail_templates: a reusable mail of an
 * organization, such as a first-contact mail, that an outreach copies into its own mail.
 *
 * A mail template is complete on creation: the name, language, subject and body are all
 * required, so there is no ready state, unlike a
 * {@link com.korur.oba.shrine.document.entity.template.DocumentTemplate} that waits for its
 * files. It has no files of its own.
 *
 * Organization scoping. Every mail template belongs to one organization and is only read
 * and written within it: every lookup goes through
 * {@link com.korur.oba.apps.otak.mailtemplate.repository.MailTemplateRepository#findByIdAndOrganizationId}
 * or the organization filter of
 * {@link com.korur.oba.apps.otak.mailtemplate.specification.MailTemplateSpecification}.
 * Its name is unique per organization and language with case ignored, which a unique
 * index on the lower-case name enforces. Deleting the organization deletes its mail
 * templates.
 *
 * Copies, not references. The mail of an outreach gets its own copy of the subject, body
 * and language, so a later change to the template does not change a mail already written.
 * Deleting a mail template keeps those mails; the database only clears their link (ON
 * DELETE SET NULL on otak.mails.mail_template_id).
 *
 * The id and the created and updated times come from {@link BaseEntity}.
 *
 * @see com.korur.oba.apps.otak.mailtemplate.repository.MailTemplateRepository
 * @see com.korur.oba.apps.otak.mailtemplate.service.MailTemplateQueryService
 */
@Entity
@Table(name = "mail_templates", schema = "otak")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MailTemplate extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Name shown in the mail template list, stored trimmed; at most 100 characters. Unique
     * per organization and language, case ignored.
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * Mail subject; at most 255 characters.
     */
    @Column(name = "subject", length = 255, nullable = false)
    private String subject;

    /**
     * Plain-text mail body. The database caps it at 10,000 characters.
     */
    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    /**
     * Organization that owns the mail template, set once on create from the request's
     * checked organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    // -------------Enums-----------//

    /**
     * Language the mail is written in; may change on update, as long as the name stays
     * unique in the new language.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "language", nullable = false)
    private DocumentLanguage language;
}
