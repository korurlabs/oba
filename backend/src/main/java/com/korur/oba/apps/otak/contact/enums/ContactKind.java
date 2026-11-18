package com.korur.oba.apps.otak.contact.enums;

/**
 * What a contact stands for at its relation: a person who works there, or the company's
 * general details such as an info@ inbox. The kind exists so the company's general details
 * are never taken for a person, for example when a member decides whom to talk to first.
 *
 * The kind decides which main flag a {@link com.korur.oba.apps.otak.contact.entity.Contact}
 * may carry: {@link #PERSON} carries
 * {@link com.korur.oba.apps.otak.contact.entity.Contact#mainPersonContact} and
 * {@link #BUSINESS} carries
 * {@link com.korur.oba.apps.otak.contact.entity.Contact#mainBusinessContact}. A relation
 * has at most one main contact of each kind; a partial unique index per flag on
 * otak.contacts refuses a second one, and a check constraint per flag refuses the flag on
 * a contact of the other kind.
 *
 * The kind is independent of {@link ContactEmailType}: a person may have a company, shared
 * or personal address, and nothing ties a business contact to a shared one.
 *
 * Mapped by name to the PostgreSQL enum otak_contact_kind_enum, whose values are declared
 * in the same order; a new constant needs a migration that adds the value to that type.
 *
 * Read by {@link com.korur.oba.apps.otak.contact.service.impl.ContactServiceImpl} to choose
 * the flag to set, clear or promote, and by
 * {@link com.korur.oba.apps.otak.contact.repository.ContactRepository} to find the contacts
 * of one kind on a relation.
 */
public enum ContactKind {

    /**
     * A person who works at the relation. The first person contact of a relation becomes
     * its main person contact, the one to talk to first. Also the kind of every contact
     * created without a kind, and of every contact that existed before kinds did.
     */
    PERSON,

    /**
     * The company's general details, such as the info@ or sales@ inbox. The first business
     * contact of a relation becomes its main business contact, the inbox to write to, which
     * the relations list shows as the company mail.
     */
    BUSINESS
}
