package com.korur.oba.apps.kazan.comment.constant.controller;

/**
 * Response message constants for comment controller operations.
 * <p>
 * These constants provide consistent success messages returned to clients
 * in API responses.
 */
public final class CommentControllerResponseConstant {

    private CommentControllerResponseConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String RESPONSE_COMMENT_CREATED = "Comment created successfully";
    public static final String RESPONSE_COMMENT_UPDATED = "Comment updated successfully";
    public static final String RESPONSE_COMMENT_DELETED = "Comment deleted successfully";
    public static final String RESPONSE_COMMENT_RETRIEVED = "Comment retrieved successfully";
    public static final String RESPONSE_COMMENT_LIST_RETRIEVED = "Comments retrieved successfully";
}
