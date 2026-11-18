package com.korur.oba.apps.kazan.sprint.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// 4. Internal
import com.korur.oba.apps.kazan.sprint.entity.Sprint;
import com.korur.oba.apps.kazan.sprint.entity.SprintSeries;
import com.korur.oba.apps.kazan.sprint.enums.SprintRecurrenceCreationMode;
import com.korur.oba.apps.kazan.sprint.enums.SprintStatus;

/**
 * Computes the schedule of a sprint series from its sprints' dates and its
 * settings.
 *
 * The series stores the length of every sprint it creates. Every time this
 * class returns is derived from sprint dates, which a sprint in a series keeps
 * at 00:00 UTC, and whole days, so every rollover and creation time also
 * falls at 00:00 UTC. The class reads no clock and no database; callers pass
 * the current time where a rule needs it.
 */
@Component
public class SprintRecurrenceCalculator {

    /**
     * Whole days from the sprint's start date to its end date.
     *
     * @param sprint the sprint
     * @return the length in days, or null when either date is missing
     */
    public Integer lengthDays(Sprint sprint) {
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate());
    }

    /**
     * The sprint whose dates drive the series' schedule: its live ACTIVE
     * sprint, or, when none runs, its PLANNED head (the live sprint with the
     * highest round).
     *
     * @param seriesSprints the live sprints of the series, highest round first
     * @return the schedule sprint, or empty when the series has neither
     */
    public Optional<Sprint> currentSprint(List<Sprint> seriesSprints) {
        for (Sprint candidate : seriesSprints) {
            if (candidate.getStatus() == SprintStatus.ACTIVE) {
                return Optional.of(candidate);
            }
        }
        return plannedHead(seriesSprints);
    }

    /**
     * The series' live sprint with the highest round when it is PLANNED: the
     * sprint created ahead of the rollover, or the first sprint of a series
     * that has not started yet.
     *
     * @param seriesSprints the live sprints of the series, highest round first
     * @return the PLANNED head, or empty when the head is not PLANNED
     */
    public Optional<Sprint> plannedHead(List<Sprint> seriesSprints) {
        if (!seriesSprints.isEmpty() && seriesSprints.getFirst().getStatus() == SprintStatus.PLANNED) {
            return Optional.of(seriesSprints.getFirst());
        }
        return Optional.empty();
    }

    /**
     * Tells whether closing the given sprint leaves its series with no ACTIVE
     * sprint and no PLANNED head, which switches an enabled series off. The
     * sprint is judged as if it were already completed, so a takeover can warn
     * before it closes the sprint.
     *
     * @param closing       the sprint about to be completed
     * @param seriesSprints the live sprints of its series, highest round first
     * @return true when nothing of the series would be left to run
     */
    public boolean closingEndsSeries(Sprint closing, List<Sprint> seriesSprints) {
        for (Sprint candidate : seriesSprints) {
            if (candidate.getStatus() == SprintStatus.ACTIVE && !candidate.getId().equals(closing.getId())) {
                return false;
            }
        }
        if (seriesSprints.isEmpty() || seriesSprints.getFirst().getId().equals(closing.getId())) {
            return true;
        }
        return seriesSprints.getFirst().getStatus() != SprintStatus.PLANNED;
    }

    /**
     * The moment the job next closes or starts a sprint of the series: the end
     * date of its ACTIVE sprint or, when none runs, the start date of its
     * PLANNED head.
     *
     * @param series        the series
     * @param seriesSprints the live sprints of the series, highest round first
     * @return the next rollover time, or null when the series is off or has nothing to close or start
     */
    public Instant nextRolloverAt(SprintSeries series, List<Sprint> seriesSprints) {
        if (!series.isEnabled()) {
            return null;
        }
        return currentSprint(seriesSprints)
                .map(sprint -> sprint.getStatus() == SprintStatus.ACTIVE ? sprint.getEndDate() : sprint.getStartDate())
                .orElse(null);
    }

    /**
     * The moment the job creates the next sprint of the series ahead of the
     * rollover: the current sprint's end date minus createDaysBefore days.
     *
     * There is none when the series is off, when the next sprint is created at
     * the end date, when a PLANNED sprint with a higher round than the current
     * one already exists, when the current sprint has no end date, or when the
     * days before do not fit inside the current sprint, so a sprint never
     * creates its successor before it has started.
     *
     * @param series        the series
     * @param seriesSprints the live sprints of the series, highest round first
     * @return the creation time, or null when no sprint is to be created ahead
     */
    public Instant nextCreationAt(SprintSeries series, List<Sprint> seriesSprints) {
        if (!series.isEnabled()
                || series.getCreationMode() != SprintRecurrenceCreationMode.DAYS_BEFORE
                || series.getCreateDaysBefore() == null) {
            return null;
        }
        Optional<Sprint> current = currentSprint(seriesSprints);
        if (current.isEmpty() || current.get().getEndDate() == null || !daysBeforeFitsLength(current.get(), series)) {
            return null;
        }
        Optional<Sprint> head = plannedHead(seriesSprints);
        boolean sprintAhead = head.isPresent() && head.get() != current.get();
        if (sprintAhead) {
            return null;
        }
        return current.get().getEndDate().minus(Duration.ofDays(series.getCreateDaysBefore()));
    }

    /**
     * Tells whether the series' days before the end date leave the creation
     * time after the given sprint's start. True when the mode does not use
     * days before or a value is missing, since there is then nothing to fit.
     *
     * @param sprint the sprint the next one is created from
     * @param series its series
     * @return false only when a days-before value is set and is not below the sprint's length
     */
    public boolean daysBeforeFitsLength(Sprint sprint, SprintSeries series) {
        Integer lengthDays = lengthDays(sprint);
        if (series.getCreationMode() != SprintRecurrenceCreationMode.DAYS_BEFORE
                || series.getCreateDaysBefore() == null
                || lengthDays == null) {
            return true;
        }
        return series.getCreateDaysBefore() < lengthDays;
    }

    /**
     * The start of the period that contains now, counted in series lengths
     * from the closing sprint's end date: the latest end date + k * length
     * (k >= 0) that is not after now. After downtime over several end dates,
     * the job rolls over once into this period and skips the periods in
     * between. Before the end date it is the end date itself.
     *
     * @param closingSprint the closing sprint, with its end date set
     * @param series        its series
     * @param now           the time of the run
     * @return the start of the next sprint
     */
    public Instant rolloverAt(Sprint closingSprint, SprintSeries series, Instant now) {
        Instant endDate = closingSprint.getEndDate();
        if (!now.isAfter(endDate)) {
            return endDate;
        }
        Duration length = Duration.ofDays(series.getLengthDays());
        long periods = Duration.between(endDate, now).dividedBy(length);
        return endDate.plus(length.multipliedBy(periods));
    }

    /**
     * The end date of the next sprint of the series that starts at rolloverAt.
     *
     * A sprint created ahead keeps the end date it was given (an admin may
     * have edited it) when the rollover is on time and that end date is after
     * the start. After a catch-up, or for a new sprint, the end is one series
     * length after the start.
     *
     * @param closingSprint the sprint being closed, with its end date set
     * @param series        its series
     * @param rolloverAt    the start of the next sprint
     * @param plannedEnd    end date of the sprint created ahead, or null when there is none
     * @return the end date of the next sprint
     */
    public Instant nextEndDate(Sprint closingSprint, SprintSeries series, Instant rolloverAt, Instant plannedEnd) {
        boolean onTime = rolloverAt.equals(closingSprint.getEndDate());
        if (onTime && plannedEnd != null && plannedEnd.isAfter(rolloverAt)) {
            return plannedEnd;
        }
        return rolloverAt.plus(Duration.ofDays(series.getLengthDays()));
    }
}
