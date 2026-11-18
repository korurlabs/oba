package com.korur.oba.apps.kazan.board.constant.controller;

/**
 * Log message constants for board controller operations.
 * <p>
 * These constants provide consistent logging at the controller layer,
 * tracking incoming requests and their completion status.
 */
public final class BoardControllerLogConstant {

    private BoardControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String BOARD_CREATE_REQUEST = "Received request to create board";
    public static final String BOARD_CREATE_COMPLETED = "Create board request completed";

    public static final String BOARD_GET_REQUEST = "Received request to get board";
    public static final String BOARD_GET_COMPLETED = "Get board request completed";

    public static final String BOARD_GET_BY_SLUG_REQUEST = "Received request to get board by slug";
    public static final String BOARD_GET_BY_SLUG_COMPLETED = "Get board by slug request completed";

    public static final String BOARD_LIST_REQUEST = "Received request to list boards";
    public static final String BOARD_LIST_COMPLETED = "List boards request completed";

    public static final String BOARD_RELATED_LIST_REQUEST = "Received request to list related boards";
    public static final String BOARD_RELATED_LIST_COMPLETED = "List related boards request completed";

    public static final String BOARD_UPDATE_REQUEST = "Received request to update board";
    public static final String BOARD_UPDATE_COMPLETED = "Update board request completed";

    public static final String BOARD_DELETE_REQUEST = "Received request to delete board";
    public static final String BOARD_DELETE_COMPLETED = "Delete board request completed";

    public static final String BOARD_MEMBER_LIST_REQUEST = "Received request to list board members";
    public static final String BOARD_MEMBER_LIST_COMPLETED = "List board members request completed";
    public static final String BOARD_MEMBER_CANDIDATE_LIST_REQUEST = "Received request to list board member candidates";
    public static final String BOARD_MEMBER_CANDIDATE_LIST_COMPLETED = "List board member candidates request completed";

    public static final String BOARD_MEMBER_ADD_REQUEST = "Received request to add board member";
    public static final String BOARD_MEMBER_ADD_COMPLETED = "Add board member request completed";

    public static final String BOARD_MEMBER_UPDATE_REQUEST = "Received request to update board member";
    public static final String BOARD_MEMBER_UPDATE_COMPLETED = "Update board member request completed";

    public static final String BOARD_MEMBER_REMOVE_REQUEST = "Received request to remove board member";
    public static final String BOARD_MEMBER_REMOVE_COMPLETED = "Remove board member request completed";

    public static final String BOARD_MEMBER_SPECIALTY_UPDATE_REQUEST = "Received request to update board member specialties";
    public static final String BOARD_MEMBER_SPECIALTY_UPDATE_COMPLETED = "Update board member specialties request completed";

    public static final String BOARD_RESTORE_REQUEST = "Received request to restore board";
    public static final String BOARD_RESTORE_COMPLETED = "Restore board request completed";

    public static final String BOARD_ADD_CHILDREN_REQUEST = "Received request to add child boards";
    public static final String BOARD_ADD_CHILDREN_COMPLETED = "Add child boards request completed";
}
