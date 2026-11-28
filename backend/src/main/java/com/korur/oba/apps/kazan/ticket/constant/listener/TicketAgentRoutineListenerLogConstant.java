package com.korur.oba.apps.kazan.ticket.constant.listener;

/**
 * Log messages of the listener that fires the routine of an agent assigned to a ticket. They carry
 * ids, the ticket key and the fire status only, never the routine secret.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class TicketAgentRoutineListenerLogConstant {

    private TicketAgentRoutineListenerLogConstant() {
    }

    public static final String ROUTINE_NOT_FIRED_FOR_NON_PERSON =
            "No agent routine fired for assignment by a non-person: organizationId={}, ticketKey={}, "
                    + "actorId={}, actorKind={}";
    public static final String ROUTINE_FIRED =
            "Agent routine fired for assignment: organizationId={}, ticketKey={}, userId={}, status={}";
    public static final String ROUTINE_FIRE_FAILED =
            "Agent routine fire failed for assignment: organizationId={}, ticketKey={}, userId={}";
}
