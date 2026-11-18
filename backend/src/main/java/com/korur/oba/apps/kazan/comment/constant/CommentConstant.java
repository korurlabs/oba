package com.korur.oba.apps.kazan.comment.constant;

/**
 * Constants for comment field names used in error messages and validation.
 * <p>
 * These constants provide consistent field naming across the comment domain,
 * particularly useful for exception error value maps.
 */
public final class CommentConstant {

    private CommentConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for comment ID.
     */
    public static final String FIELD_COMMENT_ID = "commentId";

    /**
     * Field name constant for ticket ID.
     */
    public static final String FIELD_TICKET_ID = "ticketId";

    /**
     * Field name constant for user ID.
     */
    public static final String FIELD_USER_ID = "userId";

    /**
     * Field name constant for comment content.
     */
    public static final String FIELD_CONTENT = "content";
}
