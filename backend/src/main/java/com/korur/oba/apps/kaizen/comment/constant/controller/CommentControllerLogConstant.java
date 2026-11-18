package com.korur.oba.apps.kaizen.comment.constant.controller;

/**
 * Log message constants for comment controller operations.
 * <p>
 * These constants provide consistent logging at the controller layer,
 * tracking incoming requests and their completion status.
 */
public final class CommentControllerLogConstant {

    private CommentControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String COMMENT_CREATE_REQUEST = "Received request to create comment";
    public static final String COMMENT_CREATE_COMPLETED = "Create comment request completed";

    public static final String COMMENT_GET_REQUEST = "Received request to get comment";
    public static final String COMMENT_GET_COMPLETED = "Get comment request completed";

    public static final String COMMENT_LIST_REQUEST = "Received request to list comments";
    public static final String COMMENT_LIST_COMPLETED = "List comments request completed";

    public static final String COMMENT_UPDATE_REQUEST = "Received request to update comment";
    public static final String COMMENT_UPDATE_COMPLETED = "Update comment request completed";

    public static final String COMMENT_DELETE_REQUEST = "Received request to delete comment";
    public static final String COMMENT_DELETE_COMPLETED = "Delete comment request completed";
}
