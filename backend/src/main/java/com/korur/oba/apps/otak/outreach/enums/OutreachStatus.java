package com.korur.oba.apps.otak.outreach.enums;

/**
 * Where an outreach stands in sending. Mapped by name to the PostgreSQL enum
 * otak_outreach_status_enum, in its order.
 */
public enum OutreachStatus {

    /**
     * Prepared and not sent yet; the outreach, its mail and its attachments can still change.
     */
    DRAFT,

    /**
     * Sent, or claimed by a send that is running. The outreach, its mail and every attachment
     * of the mail are then the record of what the contact received and never change again.
     */
    SENT,

    /**
     * A send was tried and failed; the outreach can be changed and sent again.
     */
    FAILED
}
