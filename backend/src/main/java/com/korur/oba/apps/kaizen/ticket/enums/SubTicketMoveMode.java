package com.korur.oba.apps.kaizen.ticket.enums;

/**
 * Defines which sub-tickets come along when a user moves a ticket to a sprint
 * or a backlog on the same board. A move to another board ignores it and
 * always carries the whole subtree, because a sub-ticket never sits on another
 * board than its parent.
 */
public enum SubTicketMoveMode {

    // Move only the requested tickets.
    NONE,

    // Also move every descendant that is not DONE and is placed where its moved ancestor was.
    UNFINISHED,

    // Also move exactly the descendants named in the request, wherever they are placed.
    SELECTED
}
