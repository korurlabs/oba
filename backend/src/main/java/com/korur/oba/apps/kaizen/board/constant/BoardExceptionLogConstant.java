package com.korur.oba.apps.kaizen.board.constant;

/**
 * Log message constants for board domain exceptions.
 * <p>
 * These constants provide consistent exception logging messages across
 * the board domain, used in validators and services when errors occur.
 */
public final class BoardExceptionLogConstant {

    private BoardExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a board is not found.
     */
    public static final String BOARD_NOT_FOUND = "Board not found: {}";

    /**
     * Log message for when a board belongs to another organization than the one the caller acts in.
     */
    public static final String BOARD_OF_OTHER_ORGANIZATION = "Board belongs to another organization than the request: boardId={}, organizationId={}";

    /**
     * Log message for when a board member is not found.
     */
    public static final String BOARD_MEMBER_NOT_FOUND = "Board member not found: boardId={}, userId={}";

    /**
     * Log message for when a board member already exists.
     */
    public static final String BOARD_MEMBER_ALREADY_EXISTS = "Board member already exists: boardId={}, userId={}";

    /**
     * Log message for when a user to add to a board is not an ACTIVE member of its organization.
     */
    public static final String BOARD_MEMBER_NOT_IN_ORGANIZATION = "User to add is not an active member of the board's organization: organizationId={}, userId={}";

    /**
     * Log message for when a member has insufficient board permissions.
     */
    public static final String BOARD_INSUFFICIENT_PERMISSION = "Insufficient board permissions: boardId={}, userId={}, role={}";

    /**
     * Log message for when a board prefix is already in use.
     */
    public static final String BOARD_PREFIX_DUPLICATE = "Board prefix already in use: prefix={}";

    /**
     * Log message for when a concurrent write took a board's slug between the free-slug
     * check and the save.
     */
    public static final String BOARD_SLUG_RACE_LOST = "Board slug {} taken by a concurrent write: boardId={}, cause={}";

    public static final String BOARD_DELETE_TICKET_ACTION_REQUIRED = "Board delete requires ticketAction: boardId={}";
    public static final String BOARD_DELETE_TARGET_BOARD_REQUIRED = "Board delete requires targetBoardId for MOVE: boardId={}";
    public static final String BOARD_DELETE_CHILD_ACTION_REQUIRED = "Board delete requires childBoardAction: boardId={}";
    public static final String BOARD_DELETE_NEW_PARENT_REQUIRED = "Board delete requires newParentBoardId for MOVE: boardId={}";
    public static final String BOARD_DELETE_CIRCULAR_PARENT = "Circular parent detected on board delete: boardId={}, newParentBoardId={}";
    public static final String BOARD_NOT_DELETED = "Board is not deleted, cannot restore: boardId={}";
    public static final String BOARD_CIRCULAR_PARENT = "Circular parent detected: boardId={}, parentBoardId={}";
    public static final String BOARD_ALREADY_CHILD = "Board is already a child of specified parent: boardId={}, parentBoardId={}";
}
