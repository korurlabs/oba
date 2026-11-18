package com.korur.oba.apps.kazan.board.constant;

/**
 * Constants for board field names used in error messages and validation.
 * <p>
 * These constants provide consistent field naming across the board domain,
 * particularly useful for exception error value maps.
 */
public final class BoardConstant {

    private BoardConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for board ID.
     */
    public static final String FIELD_BOARD_ID = "boardId";

    /**
     * Field name constant for member ID.
     */
    public static final String FIELD_MEMBER_ID = "memberId";

    /**
     * Field name constant for user ID.
     */
    public static final String FIELD_USER_ID = "userId";

    /**
     * Field name constant for role.
     */
    public static final String FIELD_ROLE = "role";

    /**
     * Field name constant for name.
     */
    public static final String FIELD_NAME = "name";

    /**
     * Field name constant for prefix.
     */
    public static final String FIELD_PREFIX = "prefix";

    /**
     * Field name constant for target board ID.
     */
    public static final String FIELD_TARGET_BOARD_ID = "targetBoardId";

    /**
     * Field name constant for new parent board ID.
     */
    public static final String FIELD_NEW_PARENT_BOARD_ID = "newParentBoardId";

    /**
     * Field name constant for parent board ID.
     */
    public static final String FIELD_PARENT_BOARD_ID = "parentBoardId";

    /**
     * Field name constant for child board IDs.
     */
    public static final String FIELD_CHILD_BOARD_IDS = "childBoardIds";

    /**
     * Field name constant for slug.
     */
    public static final String FIELD_SLUG = "slug";

    /**
     * Field name constant for organization ID.
     */
    public static final String FIELD_ORGANIZATION_ID = "organizationId";

    /**
     * Slug of a board whose name has no letter or digit, such as an emoji only.
     */
    public static final String SLUG_FALLBACK = "board";

    /**
     * Partial unique index on the slug of live boards; a write that violates it lost the
     * race for a slug that was free when it was chosen.
     */
    public static final String SLUG_UNIQUE_INDEX = "boards_slug_uk";
}
