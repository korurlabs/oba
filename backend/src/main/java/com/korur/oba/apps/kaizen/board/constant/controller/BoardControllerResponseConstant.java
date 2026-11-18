package com.korur.oba.apps.kaizen.board.constant.controller;

/**
 * Response message constants for board controller operations.
 * <p>
 * These constants provide consistent success messages returned to clients
 * in API responses.
 */
public final class BoardControllerResponseConstant {

    private BoardControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_BOARD_CREATED = "Board created successfully";
    public static final String RESPONSE_BOARD_UPDATED = "Board updated successfully";
    public static final String RESPONSE_BOARD_DELETED = "Board deleted successfully";
    public static final String RESPONSE_BOARD_RETRIEVED = "Board retrieved successfully";
    public static final String RESPONSE_BOARD_LIST_RETRIEVED = "Boards retrieved successfully";
    public static final String RESPONSE_BOARD_RELATED_LIST_RETRIEVED = "Related boards retrieved successfully";

    public static final String RESPONSE_BOARD_MEMBER_ADDED = "Board member added successfully";
    public static final String RESPONSE_BOARD_MEMBER_UPDATED = "Board member updated successfully";
    public static final String RESPONSE_BOARD_MEMBER_REMOVED = "Board member removed successfully";
    public static final String RESPONSE_BOARD_MEMBER_LIST_RETRIEVED = "Board members retrieved successfully";
    public static final String RESPONSE_BOARD_MEMBER_CANDIDATES_RETRIEVED = "Board member candidates retrieved successfully";

    public static final String RESPONSE_BOARD_MEMBER_SPECIALTIES_UPDATED = "Board member specialties updated successfully";

    public static final String RESPONSE_BOARD_RESTORED = "Board restored successfully";

    public static final String RESPONSE_BOARD_CHILDREN_ADDED = "Child boards added successfully";
}
