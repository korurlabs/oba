package com.korur.oba.apps.kazan.board.constant.service;

/**
 * Log message constants for board service operations.
 * <p>
 * These constants provide consistent logging at the service layer,
 * tracking business logic operations and their outcomes.
 */
public final class BoardServiceLogConstant {

    private BoardServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String BOARD_CREATED = "Board created: {}";
    public static final String BOARD_UPDATED = "Board updated: {}";
    public static final String BOARD_DELETED = "Board deleted: {}";

    public static final String BOARD_MEMBER_ADDED = "Board member added: {}";
    public static final String BOARD_MEMBER_UPDATED = "Board member updated: {}";
    public static final String BOARD_MEMBER_REMOVED = "Board member removed: {}";

    public static final String BOARD_MEMBER_SPECIALTIES_UPDATED = "Board member specialties updated: {}";

    public static final String BOARD_DELETE_CHILD_BOARDS_PROMOTED = "Child boards promoted to top-level for board: {}";
    public static final String BOARD_DELETE_CHILD_BOARDS_MOVED = "Child boards moved to new parent for board: {}";
    public static final String BOARD_DELETE_CHILD_BOARDS_DELETED = "Child boards recursively deleted for board: {}";
    public static final String BOARD_DELETE_TICKETS_MOVED = "Tickets moved to target board for board: {}";
    public static final String BOARD_DELETE_TICKETS_DELETED = "Tickets soft-deleted for board: {}";
    public static final String BOARD_DELETE_PROJECTS_DELETED = "Projects soft-deleted for board: {}";

    public static final String BOARD_RESTORED = "Board restored: {}";
    public static final String BOARD_RESTORE_BACKLOG = "Backlog restored for board: {}";
    public static final String BOARD_RESTORE_LABELS = "Labels restored for board: {}";
    public static final String BOARD_RESTORE_MEMBERS = "Board members restored for board: {}";
    public static final String BOARD_RESTORE_PROJECTS = "Projects restored for board: {}";
    public static final String BOARD_RESTORE_BOARD_SPRINTS = "Board-sprint links restored for board: {}";
    public static final String BOARD_RESTORE_TICKETS = "Tickets restored for board: {}";

    public static final String BOARD_MEMBER_RESTORED = "Soft-deleted board member restored: {}";

    public static final String BOARD_CHILDREN_ADDED = "Child boards added to board: boardId={}, childBoardIds={}";
    public static final String BOARD_PARENT_CHANGED = "Board parent changed: boardId={}, parentBoardId={}";
}
