package com.korur.oba.apps.otak.mail.entity;

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
 * Entity representing the mail of one MAIL outreach, a row of otak.mails: its subject,
 * plain-text body and language, an own copy of a
 * {@link com.korur.oba.apps.otak.mailtemplate.entity.MailTemplate} or written by hand.
 *
 * A mail has no name: the title of its outreach names it. The files it carries are shrine
 * attachments with entity type MAIL and the mail's id as parent, written through
 * /storage/attachments. A composite foreign key keeps the relation in the mail's
 * organization; deleting the relation deletes its mails, and the relation delete removes
 * their attachments.
 *
 * The mail is reached only through its outreach: the outreach status is the one edit gate,
 * so a mail never changes once its outreach is SENT. The relation, organization and
 * template are plain UUID columns, so loading a mail never loads another domain's rows.
 * The id and the audit timestamps come from {@link BaseEntity}.
 *
 * @see com.korur.oba.apps.otak.mail.service.MailService
 * @see com.korur.oba.apps.otak.mail.service.MailQueryService
 */
@Entity
@Table(name = "mails", schema = "otak")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Mail extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Mail subject; at most 255 characters.
     */
    @Column(name = "subject", length = 255, nullable = false)
    private String subject;

    /**
     * Plain-text mail body, as typed; the send escapes it into HTML. The database caps it at
     * 10,000 characters.
     */
    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    /**
     * Organization that owns the mail, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Relation of the mail's outreach; fixed after create, because the mail belongs to an
     * outreach of this relation.
     */
    @Column(name = "relation_id", nullable = false, updatable = false)
    private UUID relationId;

    /**
     * Mail template the mail was copied from, provenance only and never read for content;
     * null for a mail written by hand. Not updatable, so a save never writes back an id the
     * database has already cleared because the template was deleted.
     */
    @Column(name = "mail_template_id", updatable = false)
    private UUID mailTemplateId;

    // -------------Enums-----------//

    /**
     * Language the mail is written in; may change on update.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "language", nullable = false)
    private DocumentLanguage language;
}
