package com.korur.oba.apps.kazan.comment.constant.service;

/**
 * Log message constants for comment service operations.
 * <p>
 * These constants provide consistent logging at the service layer,
 * tracking business logic operations and their outcomes.
 */
public final class CommentServiceLogConstant {

    private CommentServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String COMMENT_CREATED = "Comment created: {}";
    public static final String COMMENT_UPDATED = "Comment updated: {}";
    public static final String COMMENT_DELETED = "Comment deleted: {}";
}
