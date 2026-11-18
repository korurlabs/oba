package com.korur.oba.apps.kazan.sprint.enums;

/**
 * Defines where the unfinished tickets of a repeating sprint go when it rolls over.
 */
public enum SprintRecurrenceTicketAction {

    // Unfinished tickets move into the next sprint of the chain.
    MOVE_TO_NEXT_SPRINT,

    // Unfinished tickets move to the backlog of each ticket's own board.
    MOVE_TO_BACKLOG
}
