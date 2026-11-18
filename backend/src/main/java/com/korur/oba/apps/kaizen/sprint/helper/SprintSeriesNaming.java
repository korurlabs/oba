package com.korur.oba.apps.kaizen.sprint.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaizen.sprint.constant.SprintRecurrenceConstant;

/**
 * The naming rule of a sprint series: a sprint of an enabled series is named
 * "{series name} {round}", and a sprint that starts a series gives its name to
 * the series with a trailing round split off.
 *
 * Used by the series service, the lifecycle helper and the recurrence job, so
 * a sprint created by a user and one created by the job are named alike.
 */
public final class SprintSeriesNaming {

    private static final Pattern SERIES_NAME = Pattern.compile(SprintRecurrenceConstant.SERIES_NAME_PATTERN);

    private SprintSeriesNaming() {
        // Private constructor to prevent instantiation
    }

    /**
     * Splits a sprint name into a series name and a round. A trailing
     * whitespace and 1 to 6 digits of at least 1 are the round, as in
     * "Sprint 7"; any other name is the whole series name with round 1, so
     * "Atasprint 0" and "X 1234567" keep their digits in the name.
     *
     * @param sprintName the sprint name, not null
     * @return the trimmed series name and its round
     */
    public static SeriesName split(String sprintName) {
        String trimmed = sprintName.trim();
        Matcher matcher = SERIES_NAME.matcher(trimmed);
        if (matcher.matches()) {
            int round = Integer.parseInt(matcher.group(2));
            if (round >= SprintRecurrenceConstant.SERIES_FIRST_ROUND) {
                return new SeriesName(matcher.group(1), round);
            }
        }
        return new SeriesName(trimmed, SprintRecurrenceConstant.SERIES_FIRST_ROUND);
    }

    /**
     * The name of the sprint with the given round in a series.
     *
     * @param seriesName the series name
     * @param round      the sprint's round
     * @return "{seriesName} {round}"
     */
    public static String sprintName(String seriesName, int round) {
        return String.format(SprintRecurrenceConstant.SERIES_SPRINT_NAME_FORMAT, seriesName, round);
    }

    /**
     * A series name with the round taken from a sprint name.
     *
     * @param name  the series name
     * @param round the round, at least 1
     */
    public record SeriesName(String name, int round) {
    }
}
