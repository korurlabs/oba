package com.korur.oba.apps.otak.outreach.enums;

/**
 * Channel an outreach goes through. Mapped by name to the PostgreSQL enum
 * otak_outreach_type_enum, in its order. Set on create and never changed afterwards,
 * because only a MAIL outreach has a mail, and the database ties the mail link and the
 * mail sender to this value.
 */
public enum OutreachType {

    /**
     * An e-mail whose subject, body and attachments come from the outreach's own mail,
     * sent through Microsoft Graph.
     */
    MAIL,

    /**
     * A WhatsApp message sent outside Oba; the outreach has no mail and is recorded as done.
     */
    WHATSAPP,

    /**
     * A phone call made outside Oba; the outreach has no mail and is recorded as done.
     */
    CALL
}
