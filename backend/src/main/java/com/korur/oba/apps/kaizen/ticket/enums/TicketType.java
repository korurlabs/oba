package com.korur.oba.apps.kaizen.ticket.enums;

/**
 * Defines the type classification of a ticket.
 *
 * INCIDENT uses the P1 to P3 priority scale; every other type uses LOWEST to HIGHEST.
 */
public enum TicketType {

    // Known defect that should be resolved fast.
    BUG,

    // New behaviour or capability for users.
    FEATURE,

    // Shortcut or neglected code that slows future work and needs paying back.
    TECH_DEBT,

    // Better behaviour of something that already works.
    IMPROVEMENT,

    // Existing features going wrong in production, needs to be fixed urgently.
    INCIDENT,

    // Issue that needs research before it can be resolved; typically takes time.
    PROBLEM,

    // Code restructuring with no change in behaviour.
    REFACTOR,

    /**
     * Former name of FEATURE, kept so older CLIs and scripts that still send it keep working.
     *
     * Accepted on input only: TicketTypeNormalizer converts it to FEATURE before every save,
     * and the database enum no longer has this value, so it is never stored or returned.
     *
     * @deprecated send FEATURE instead
     */
    @Deprecated
    TASK
}
