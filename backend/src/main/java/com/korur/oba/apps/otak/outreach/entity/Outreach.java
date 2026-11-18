package com.korur.oba.apps.otak.outreach.entity;

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
import java.time.Instant;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.outreach.enums.OutreachStatus;
import com.korur.oba.apps.otak.outreach.enums.OutreachType;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * Entity representing an outreach: one reach-out to one contact of one relation, through the
 * channel its {@link OutreachType} names. It holds no content of its own. A MAIL outreach
 * holds only the id of its {@link com.korur.oba.apps.otak.mail.entity.Mail}, which holds the
 * subject and the body; the files sent with it are attachments of that mail. A WHATSAPP or
 * CALL outreach happens outside Oba, has no mail and is only recorded as done.
 *
 * Composite foreign keys keep the mail on the outreach's relation, and the relation in the
 * outreach's organization. Once SENT the outreach, its mail and the mail's attachments
 * never change, so they stay the record of what the contact
 * received; the contact may later be deleted, which clears contactId but keeps sentTo.
 *
 * The status and the send columns (sentTo, sentAt, mailSenderId) are not updatable: only
 * the conditional updates of
 * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository} that claim a send
 * and mark it failed change them. An edit that loaded the outreach before a send claimed it therefore never
 * writes DRAFT and empty send columns back over the record of a sent mail.
 *
 * Organization scoping: organizationId is set once on create and never updated; every
 * read goes through {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository}
 * methods and specifications that take it, so an outreach is only ever seen within its
 * organization.
 *
 * Lifecycle: created {@link OutreachStatus#DRAFT} by
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#create}, changed by
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#update} while not
 * SENT, claimed {@link OutreachStatus#SENT} and possibly marked {@link OutreachStatus#FAILED} by
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#send} (MAIL) or
 * recorded {@link OutreachStatus#SENT} as done by
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#complete} (WHATSAPP and
 * CALL), and removed by
 * {@link com.korur.oba.apps.otak.outreach.service.impl.OutreachServiceImpl#delete} while not SENT.
 *
 * @see com.korur.oba.apps.otak.outreach.repository.OutreachRepository
 * @see com.korur.oba.apps.otak.outreach.validator.OutreachValidator#ensureEditable
 */
@Entity
@Table(name = "outreaches", schema = "otak")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Outreach extends BaseEntity {

    // -------------Atts-----------//

    /**
     * Name of the outreach in the lists of the organization, stored trimmed; it also names
     * the outreach's mail, which has no name of its own.
     */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /**
     * Where the outreach went, copied from the contact when it is claimed; set once SENT. For
     * MAIL the contact's mail address, for WHATSAPP and CALL the contact's phone number. Kept
     * after a failed send, as the address of the last attempt, and after the contact is
     * deleted, as the record of who was reached. Written only by
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#claimForSending}.
     */
    @Column(name = "sent_to", length = 254, updatable = false)
    private String sentTo;

    /**
     * Moment the send claimed the outreach, or the moment a WHATSAPP or CALL outreach was
     * recorded as done; set once SENT, cleared when a send fails. Written only by
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#claimForSending} and
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#markSendFailed}.
     */
    @Column(name = "sent_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", updatable = false)
    private Instant sentAt;

    /**
     * Organization that owns the outreach, set once on create from the request's checked
     * organization and never from a body.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * Relation the outreach goes to; fixed after create, because its contact and its mail
     * belong to this relation.
     */
    @Column(name = "relation_id", nullable = false, updatable = false)
    private UUID relationId;

    /**
     * Contact of the relation the outreach goes to; null once that contact is deleted, which
     * the database does (ON DELETE SET NULL) so the outreach outlives its contact. Such an
     * outreach needs a new contact before it can be sent.
     */
    @Column(name = "contact_id")
    private UUID contactId;

    /**
     * Mail that holds the subject, the body and the attachments; owned by this outreach
     * alone and fixed after create. Set for MAIL and null for WHATSAPP and CALL, which the
     * check outreaches_mail_id_ck enforces. A unique index keeps one mail to one outreach,
     * and a composite foreign key keeps it on the outreach's relation.
     */
    @Column(name = "mail_id", updatable = false)
    private UUID mailId;

    /**
     * Mail sender the outreach was sent from; null until sent, or once that sender is deleted
     * (ON DELETE SET NULL). MAIL only: always null for WHATSAPP and CALL, which the check
     * outreaches_mail_sender_id_ck enforces. Written only by
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#claimForSending}.
     */
    @Column(name = "mail_sender_id", updatable = false)
    private UUID mailSenderId;

    // -------------Enums-----------//

    /**
     * Channel of the outreach, taken from the create request and never updated: the mail
     * link and the mail sender depend on it. Stored as the PostgreSQL enum
     * otak_outreach_type_enum.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, updatable = false)
    private OutreachType type;

    /**
     * Where the outreach stands in sending. Inserted as {@link OutreachStatus#DRAFT} and
     * never updated through the entity: only the conditional updates of
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#claimForSending} and
     * {@link com.korur.oba.apps.otak.outreach.repository.OutreachRepository#markSendFailed}
     * change it, so a stale entity can never undo a send. Stored as the PostgreSQL enum
     * otak_outreach_status_enum.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, updatable = false)
    private OutreachStatus status;
}
