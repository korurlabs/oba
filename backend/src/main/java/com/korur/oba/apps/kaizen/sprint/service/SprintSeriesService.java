package com.korur.oba.apps.kaizen.sprint.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.board.entity.Board;
import com.korur.oba.apps.kaizen.sprint.dto.request.SprintSeriesRequest;
import com.korur.oba.apps.kaizen.sprint.dto.response.SprintDetailResponse;
import com.korur.oba.apps.kaizen.sprint.entity.Sprint;
import com.korur.oba.apps.kaizen.sprint.entity.SprintSeries;
import com.korur.oba.shrine.identity.entity.User;

/**
 * Write operations on sprint series: the name, counter, "repeat
 * automatically" settings and boards shared by the sprints of one series.
 *
 * A series is changed from one of its sprints, so an admin of any board linked
 * to that sprint changes the whole series. Every writer locks the series row
 * before it writes any sprint of the series, in the order the recurrence job
 * takes its locks. Reads go through the sprint detail response.
 */
public interface SprintSeriesService {

    /**
     * Replaces the series settings of a sprint. On a sprint outside any series,
     * switching on starts a series named after the sprint with its trailing
     * round split off; switching a series on again takes the series name from
     * the sprint's current name and renames the series' unfinished sprints.
     * Requires ADMIN on the board in the path.
     *
     * @return the sprint detail with its series
     */
    SprintDetailResponse updateSeries(
            User user, UUID organizationId, UUID boardId, UUID sprintId, SprintSeriesRequest request);

    /**
     * Starts an enabled series with a new sprint as round 1, linked to the
     * sprint's boards in order. A null or switched-off request does nothing.
     * Checks no permission: the caller has already required ADMIN for the
     * sprint's board and named the sprint "{seriesName} 1".
     *
     * @param sprint     the saved new sprint
     * @param seriesName the trimmed, validated series name
     * @param request    the settings from the create request, nullable
     */
    void applyOnCreate(Sprint sprint, String seriesName, SprintSeriesRequest request);

    /**
     * Renames the sprint's series and every live sprint of it that is not
     * COMPLETED, with their slugs following. The caller has locked the series
     * and checked the naming rule and its permission.
     *
     * @param sprint     a sprint of an enabled series
     * @param seriesName the new series name, validated
     */
    void renameSeries(Sprint sprint, String seriesName);

    /**
     * Checks the new dates of a sprint in an enabled series and recomputes when
     * the series creates its next sprint. Does nothing for a sprint outside an
     * enabled series or a COMPLETED sprint. The caller has locked the series.
     *
     * @param sprint the sprint with its new dates applied
     */
    void onSprintDatesChanged(Sprint sprint);

    /**
     * Recomputes when the series of a sprint creates its next sprint, after a
     * user started, completed, took over or deleted the sprint. Which sprint
     * runs and whether a sprint was created ahead decide that time, and the
     * job finds a series only through the stored time, so it is kept right
     * here, in the same transaction as the change. Does nothing for a sprint
     * outside any series. The caller has locked the series.
     *
     * An enabled series that the change left without a running or planned
     * sprint is switched off instead, so that it does not keep its boards
     * from other series while it can never run again.
     *
     * @param sprint the sprint whose status changed, or that was deleted
     */
    void rescheduleSeries(Sprint sprint);

    /**
     * Prepares the next round of a series whose ACTIVE sprint a user is
     * completing by hand, so the team does not wait for the old end date. The
     * next round runs from the completion day at 00:00 UTC: a sprint created
     * ahead is redated to that day (its end kept only when the closing sprint
     * ends that day, else one series length later), otherwise a new round is
     * created for one series length. Either is linked to the series' live
     * boards in their order. A series with no live board is switched off with
     * FAILED, as the job does. Call it before the closing sprint is completed,
     * so its unfinished tickets can move into the returned sprint; the sprint
     * is still PLANNED and is started by {@link #startAdvancedSprint}. The caller holds
     * the series lock and has checked its permission.
     *
     * @param closingSprint the ACTIVE sprint being completed
     * @param now           the time of the completion
     * @return the next round, or empty for a sprint outside an enabled series or a series switched off here
     */
    Optional<Sprint> advanceOnComplete(Sprint closingSprint, Instant now);

    /**
     * Starts the round {@link #advanceOnComplete} returned, once the closing sprint is
     * COMPLETED, and records the run on the series. A board of the round that
     * holds another ACTIVE sprint leaves it PLANNED and records BLOCKED, so the
     * job starts it once the board is free; no other sprint is closed.
     *
     * @param nextSprint the PLANNED next round of the series
     * @param now        the time of the completion
     */
    void startAdvancedSprint(Sprint nextSprint, Instant now);

    /**
     * Adds a newly linked board to the series of a sprint that is not
     * COMPLETED, at the last position, so later sprints are linked to it too.
     *
     * @param sprint the sprint just linked
     * @param board  the linked board
     */
    void onBoardLinked(Sprint sprint, Board board);

    /**
     * Removes an unlinked board from the series of a sprint that is not
     * COMPLETED, so later sprints are no longer linked to it.
     *
     * @param sprint  the sprint just unlinked
     * @param boardId the unlinked board
     */
    void onBoardUnlinked(Sprint sprint, UUID boardId);

    /**
     * Locks the series of a sprint, waiting while the job or another save holds
     * it, and reloads it so the caller works on the committed counter and
     * settings. Call it before writing the sprint.
     *
     * @param sprint the sprint
     * @return the locked series, or empty when the sprint has none
     */
    Optional<SprintSeries> lockSeries(Sprint sprint);
}
