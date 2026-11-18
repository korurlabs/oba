package com.korur.oba.apps.kazan.ticket.enums;

/**
 * What a reported incident did to a board.
 */
public enum IncidentOutcome {

    // A new incident ticket was created in the board's backlog.
    CREATED,

    // Nothing was written: the incident already has an open ticket, or no new ticket was allowed.
    IGNORED
}
