package com.korur.oba.apps.kazan.sprint.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Set;

// 4. Internal

/**
 * Constants for sprint field names used in error messages and validation.
 * <p>
 * These constants provide consistent field naming across the sprint domain,
 * particularly useful for exception error value maps.
 */
public final class SprintConstant {

    private SprintConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for sprint ID.
     */
    public static final String FIELD_SPRINT_ID = "sprintId";

    /**
     * Field name constant for board ID.
     */
    public static final String FIELD_BOARD_ID = "boardId";

    /**
     * Field name constant for board name.
     */
    public static final String FIELD_BOARD_NAME = "boardName";

    /**
     * Field name constant for the names of the boards that block a sprint start.
     */
    public static final String FIELD_BOARD_NAMES = "boardNames";

    /**
     * Separator between board names in an error value.
     */
    public static final String BOARD_NAMES_SEPARATOR = ", ";

    /**
     * Field name constant for the board a sprint is created on, when a child board of it
     * is checked.
     */
    public static final String FIELD_ROOT_BOARD_ID = "rootBoardId";

    /**
     * Highest number of child boards a sprint create may link, the same limit as a bulk link.
     */
    public static final int MAX_CHILD_BOARDS = 50;

    /**
     * Field name constant for sprint name.
     */
    public static final String FIELD_NAME = "name";

    /**
     * Field name constant for sprint status.
     */
    public static final String FIELD_STATUS = "status";

    /**
     * Field name constant for start date.
     */
    public static final String FIELD_START_DATE = "startDate";

    /**
     * Field name constant for end date.
     */
    public static final String FIELD_END_DATE = "endDate";

    /**
     * Slug of a sprint whose name has no letter or digit, such as an emoji only. It is
     * itself a reserved segment, so such a sprint ends up as "sprint-1" or later.
     */
    public static final String SLUG_FALLBACK = "sprint";

    /**
     * Fixed path segments under a board page in the web app. A sprint slug sits at the same
     * level, so it may never equal one of them; a sprint named after one gets a suffix.
     */
    public static final Set<String> RESERVED_SLUGS = Set.of("all", "backlog", "roadmap", "sprint", "ticket");

    /**
     * Partial unique index on the slugs of a board's live links; a write that violates it
     * lost the race for a slug that was free when it was chosen.
     */
    public static final String SLUG_UNIQUE_INDEX = "board_sprints_board_id_slug_uk";
}
