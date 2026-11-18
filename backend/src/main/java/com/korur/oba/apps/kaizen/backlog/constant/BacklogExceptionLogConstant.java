package com.korur.oba.apps.kaizen.backlog.constant;

/**
 * Log message constants for backlog domain exceptions.
 * <p>
 * These constants provide consistent exception logging messages across
 * the backlog domain, used in validators and services when errors occur.
 */
public final class BacklogExceptionLogConstant {

    private BacklogExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a backlog is not found.
     */
    public static final String BACKLOG_NOT_FOUND = "Backlog not found: {}";

    /**
     * Log message for when a backlog already exists for the board.
     */
    public static final String BACKLOG_ALREADY_EXISTS = "Backlog already exists for board: boardId={}";

    /**
     * Log message for when a backlog cannot be deleted.
     */
    public static final String BACKLOG_CANNOT_DELETE = "Backlog cannot be deleted: backlogId={}";
}
