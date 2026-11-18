package com.korur.oba.apps.otak.mailsender.enums;

/**
 * Kind of mailbox a mail sender is, chosen by the admin who adds it. It decides how the
 * address is looked up in the Microsoft 365 tenant. Mapped by name to the PostgreSQL enum
 * otak_email_type_enum, in its order.
 */
public enum EmailType {

    /**
     * A user mailbox, found by its address as the user principal name or object id.
     */
    PERSONAL,

    /**
     * A shared mailbox, found by its mail property among the member accounts of the tenant,
     * so its address may differ from its user principal name.
     */
    SHARED
}
