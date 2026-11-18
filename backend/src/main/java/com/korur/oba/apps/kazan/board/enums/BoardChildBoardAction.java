package com.korur.oba.apps.kazan.board.enums;

/**
 * Defines what to do with child boards when a parent board is deleted.
 */
public enum BoardChildBoardAction {

    // Promote child boards to top-level (remove parent reference).
    PROMOTE,

    // Move child boards to a new parent board.
    MOVE,

    // Soft-delete all child boards recursively.
    DELETE
}
