package com.korur.oba.apps.otak.contact.entity;

// 1. Framework-level

// 2. Third-party
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

// 3. Java SDK
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.contact.enums.ContactEmailType;
import com.korur.oba.apps.otak.contact.enums.ContactKind;
import com.korur.oba.dojo.entity.BaseEntity;

/**
 * A contact of a relation (a lead, customer or supplier), stored in otak.contacts: either
 * a person to talk to at the relation or the relation's general company details such as
 * an info@ inbox. {@link #kind} tells the two apart (see {@link ContactKind}).
 *
 * Organization scoping: a contact belongs to one relation and carries the relation's
 * organization in {@link #organizationId}. The composite foreign key
 * contacts_relation_id_organization_id_fk on (relation_id, organization_id) keeps the two
 * equal, so a contact can never point to a relation of another organization, and every
 * query of {@link com.korur.oba.apps.otak.contact.repository.ContactRepository} names the
 * organization, so a contact of another organization is never found.
 *
 * Main contacts: a relation has at most one main person contact ({@link #mainPersonContact})
 * and at most one main business contact ({@link #mainBusinessContact}). The database holds
 * that rule with a partial unique index per flag, and a check constraint per flag keeps
 * each flag on contacts of its own kind. The service keeps a relation that has contacts of
 * a kind with exactly one main contact of that kind: the first contact of a kind becomes
 * main, and when the main contact leaves its kind the oldest remaining one is promoted.
 *
 * Rows are deleted outright, without a soft-delete flag, and the database removes them
 * together with their relation.
 *
 * Written by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl}, read
 * by {@link com.korur.oba.apps.otak.contact.service.impl.ContactQueryServiceImpl}, and read
 * as main contacts for the relations list through
 * {@link com.korur.oba.apps.otak.relation.repository.RelationRepository#findMainContacts}.
 */
@Entity
@Table(name = "contacts", schema = "otak")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Contact extends BaseEntity {

    // -------------Atts-----------//

    /**
     * The person's full name, or the label of the company details for a business contact.
     * The contacts list of a relation is sorted by it after the main contacts.
     */
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /**
     * Person or business contact; {@link ContactKind#PERSON} when not given. It decides
     * which of the two main flags the contact may carry: contacts_main_person_contact_ck
     * allows {@link #mainPersonContact} only on {@link ContactKind#PERSON} and
     * contacts_main_business_contact_ck allows {@link #mainBusinessContact} only on
     * {@link ContactKind#BUSINESS}, so a contact never carries both flags. Changing the kind
     * therefore clears the flag of the old kind first.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "kind", nullable = false)
    private ContactKind kind = ContactKind.PERSON;

    /**
     * Job title or function at the relation.
     */
    @Column(name = "role", length = 100, nullable = true)
    private String role;

    /**
     * The e-mail address, if known. For the main person contact the relations list shows it
     * as the contact mail, for the main business contact as the company mail.
     */
    @Column(name = "email", length = 254, nullable = true)
    private String email;

    /**
     * Kind of the e-mail address; null when it is not known. Only set together with an
     * address, which the service checks before it saves.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "email_type", nullable = true)
    private ContactEmailType emailType;

    /**
     * The phone number, if known.
     */
    @Column(name = "phone", length = 50, nullable = true)
    private String phone;

    /**
     * The LinkedIn profile, if known, as a full http or https address.
     */
    @Column(name = "linkedin_url", length = 500, nullable = true)
    private String linkedinUrl;

    /**
     * Page the contact was found on, which the person is told about at first contact.
     * It is set on create only and an update keeps it, so the provenance of a found
     * person can not be lost by an edit that does not send it.
     */
    @Column(name = "source_url", length = 500, nullable = true)
    private String sourceUrl;

    /**
     * Whether the person decides on a deal at the relation. Never null: a request that
     * leaves it out or sends null stores false.
     */
    @Builder.Default
    @Column(name = "decision_maker", nullable = false)
    private boolean decisionMaker = false;

    /**
     * The person to talk to first at the relation. A relation with person contacts has
     * exactly one main person contact: the partial unique index
     * contacts_relation_id_main_person_contact_uk on relation_id where the flag is true
     * refuses a second one, and contacts_main_person_contact_ck keeps the flag off business
     * contacts. Because the index checks every row write, the service unmarks the previous
     * main contact with
     * {@link com.korur.oba.apps.otak.contact.repository.ContactRepository#clearMainPersonContact}
     * before it marks a new one. The relations list shows this contact's name and e-mail.
     */
    @Builder.Default
    @Column(name = "main_person_contact", nullable = false)
    private boolean mainPersonContact = false;

    /**
     * The company inbox to write to at the relation. A relation with business contacts has
     * exactly one main business contact: the partial unique index
     * contacts_relation_id_main_business_contact_uk on relation_id where the flag is true
     * refuses a second one, and contacts_main_business_contact_ck keeps the flag off person
     * contacts. The service unmarks the previous one with
     * {@link com.korur.oba.apps.otak.contact.repository.ContactRepository#clearMainBusinessContact}
     * before it marks a new one. The relations list shows this contact's e-mail as the
     * company mail.
     */
    @Builder.Default
    @Column(name = "main_business_contact", nullable = false)
    private boolean mainBusinessContact = false;

    /**
     * Organization of the relation, set once on create from the relation that was read for
     * the request's checked organization, never from a request body, and never updated.
     * Every repository query filters on it, which is what keeps one organization's contacts
     * out of another's reads.
     */
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    /**
     * The relation (lead, customer or supplier) the contact belongs to, set once on create
     * and never updated: a contact does not move between relations.
     */
    @Column(name = "relation_id", nullable = false, updatable = false)
    private UUID relationId;
}
