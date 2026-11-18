package com.korur.oba.apps.otak.contact.enums;

/**
 * Kind of e-mail address a contact has, so a message is written for the right reader.
 * Mapped by name to the PostgreSQL enum otak_contact_email_type_enum, in its order.
 */
public enum ContactEmailType {

    /**
     * The person's own address at their company.
     */
    COMPANY,

    /**
     * A team or company inbox that others also read, such as info@ or sales@.
     */
    SHARED,

    /**
     * The person's private address, such as one at gmail, hotmail or yahoo.
     */
    PERSONAL
}
