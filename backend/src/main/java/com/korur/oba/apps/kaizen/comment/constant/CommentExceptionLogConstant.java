package com.korur.oba.apps.kaizen.comment.constant;

/**
 * Log message constants for comment domain exceptions.
 * <p>
 * These constants provide consistent exception logging messages across
 * the comment domain, used in validators and services when errors occur.
 */
public final class CommentExceptionLogConstant {

    private CommentExceptionLogConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Log message for when a comment is not found.
     */
    public static final String COMMENT_NOT_FOUND = "Comment not found: {}";

    /**
     * Log message for when a user is not authorized to modify a comment.
     */
    public static final String COMMENT_UNAUTHORIZED_MODIFICATION = "Unauthorized comment modification: commentId={}, userId={}";

    /**
     * Log message for when a comment cannot be deleted.
     */
    public static final String COMMENT_CANNOT_DELETE = "Comment cannot be deleted: commentId={}";
}
