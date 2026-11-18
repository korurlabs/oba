package com.korur.oba.apps.kaizen.board.enums;

/**
 * Defines the role of a user within a board.
 */
public enum BoardMemberRole {

    // Full control over the board including member management and deletion.
    OWNER,

    // Administrative role for board configuration and member management.
    ADMIN,

    // Standard member role with contribution permissions.
    MEMBER,

    // Read-only access to board contents.
    VIEWER
}
