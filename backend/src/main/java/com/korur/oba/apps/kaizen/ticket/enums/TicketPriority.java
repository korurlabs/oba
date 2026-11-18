package com.korur.oba.apps.kaizen.ticket.enums;

/**
 * Defines the priority level of a ticket.
 *
 * Every ticket type except {@link TicketType#INCIDENT} uses the scale {@link #LOWEST} to
 * {@link #HIGHEST}; {@link TicketType#INCIDENT} uses {@link #P1} to {@link #P3}. Both scales
 * share one field, so a list sorted by priority interleaves them by urgency.
 *
 * The declaration order is the sort order, ascending by urgency. It must match the order
 * of the PostgreSQL enum kaizen_ticket_priority_enum, because PostgreSQL sorts an enum
 * column by declaration order and every ORDER BY priority relies on it. A new value goes
 * in at its urgency position, here and in the database (ALTER TYPE ... ADD VALUE ...
 * BEFORE or AFTER), never appended at the end.
 */
public enum TicketPriority {

    // Every type except INCIDENT. Minimal urgency, can be addressed when convenient.
    LOWEST,

    // Every type except INCIDENT. Below normal urgency.
    LOW,

    // Every type except INCIDENT. Standard urgency level.
    MEDIUM,

    // INCIDENT only. Normal priority, can be addressed when convenient. Should be fixed in 3 days.
    P3,

    // Every type except INCIDENT. Above normal urgency, should be addressed promptly.
    HIGH,

    // Every type except INCIDENT. Critical urgency requiring immediate attention.
    HIGHEST,

    // INCIDENT only. Urgent, requires immediate attention. Should be fixed within 4 hours.
    P2,

    // INCIDENT only. Critical, requires immediate attention. Should be fixed within 1 hour.
    P1
}
