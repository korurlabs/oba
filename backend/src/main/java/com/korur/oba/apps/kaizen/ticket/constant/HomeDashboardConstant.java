package com.korur.oba.apps.kaizen.ticket.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaizen.ticket.enums.MissionRank;

/**
 * Windows, limits and scoring of the home dashboard's ticket sections.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class HomeDashboardConstant {

    private HomeDashboardConstant() {
    }

    /**
     * Days without an edit or a comment after which a backlog ticket counts as stale.
     */
    public static final int STALE_BACKLOG_DAYS = 30;

    /**
     * The most stale backlog tickets listed at once; the total is reported separately.
     */
    public static final int STALE_BACKLOG_LIMIT = 50;

    /**
     * Days back from now that the LAST_30_DAYS leaderboard counts finished tickets.
     */
    public static final int LEADERBOARD_WINDOW_DAYS = 30;

    /**
     * Decimal places of a leaderboard point total. Points are split between assignees,
     * so a total is a fraction; one place keeps the list readable.
     */
    public static final int LEADERBOARD_POINTS_SCALE = 1;

    /**
     * Decimal places kept while points are split and summed, before the total is rounded
     * to {@link #LEADERBOARD_POINTS_SCALE}, so a sum of thirds does not drift.
     */
    public static final int LEADERBOARD_POINTS_WORKING_SCALE = 10;

    /**
     * Decimal places of a point on the burndown's ideal line. The line falls by a fraction
     * of a ticket per day; one place is enough to draw it.
     */
    public static final int BURNDOWN_IDEAL_SCALE = 1;

    /**
     * Days ahead of today within which an open milestone's target date counts as an
     * upcoming deadline. Overdue milestones are always included.
     */
    public static final int DEADLINE_WINDOW_DAYS = 60;

    /**
     * The most deadlines listed at once; the roadmap shows the rest.
     */
    public static final int DEADLINE_LIMIT = 8;

    /**
     * Leaderboard points per ticket size, on a Fibonacci scale so a large ticket outweighs
     * several small ones. Every ticket has a size, so every rank has an entry. The map is
     * unmodifiable, because a shared mutable map would let one caller change the scoring
     * of every other caller.
     */
    public static final Map<MissionRank, Integer> MISSION_RANK_POINTS = Collections.unmodifiableMap(
            new EnumMap<>(Map.of(
                    MissionRank.XS, 1,
                    MissionRank.S, 2,
                    MissionRank.M, 3,
                    MissionRank.L, 5,
                    MissionRank.XL, 8,
                    MissionRank.XXL, 13)));
}
