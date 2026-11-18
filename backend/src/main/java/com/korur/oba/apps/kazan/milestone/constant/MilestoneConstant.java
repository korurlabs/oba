package com.korur.oba.apps.kazan.milestone.constant;

/**
 * Field name constants for the milestone domain, used in exception value maps.
 */
public final class MilestoneConstant {

    private MilestoneConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for milestone ID.
     */
    public static final String FIELD_MILESTONE_ID = "milestoneId";

    /**
     * Field name constant for board ID.
     */
    public static final String FIELD_BOARD_ID = "boardId";

    /**
     * Field name constant for ticket ID.
     */
    public static final String FIELD_TICKET_ID = "ticketId";

    /**
     * Field name constant for start date.
     */
    public static final String FIELD_START_DATE = "startDate";

    /**
     * Field name constant for target date.
     */
    public static final String FIELD_TARGET_DATE = "targetDate";

    /**
     * Field name constant for the roadmap window start.
     */
    public static final String FIELD_FROM = "from";

    /**
     * Field name constant for the roadmap window end.
     */
    public static final String FIELD_TO = "to";

    /**
     * Field name constant for the milestone a dependent milestone depends on.
     */
    public static final String FIELD_DEPENDS_ON_MILESTONE_ID = "dependsOnMilestoneId";

    /**
     * Field name constant for the board of the milestone depended on.
     */
    public static final String FIELD_DEPENDS_ON_BOARD_ID = "dependsOnBoardId";

    /**
     * Colour format shared with board and label colours: a hex web colour.
     */
    public static final String COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";

    /**
     * Validation message for a colour that does not match COLOR_PATTERN.
     */
    public static final String COLOR_MESSAGE = "Color must be a hex colour like #3B82F6";

    /**
     * Days before the target date from which a lagging milestone counts as at risk.
     */
    public static final long AT_RISK_WINDOW_DAYS = 14L;
}
