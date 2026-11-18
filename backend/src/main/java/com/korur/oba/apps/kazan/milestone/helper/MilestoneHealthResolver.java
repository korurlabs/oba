package com.korur.oba.apps.kazan.milestone.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

// 4. Internal
import com.korur.oba.apps.kazan.milestone.constant.MilestoneConstant;
import com.korur.oba.apps.kazan.milestone.entity.Milestone;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneHealth;
import com.korur.oba.apps.kazan.milestone.enums.MilestoneStatus;

/**
 * Derives the health of a milestone from its dates and ticket progress.
 *
 * A closed milestone is DONE. An open one past its target date is OVERDUE.
 * Within the last 14 days before the target, it is AT_RISK when the share of
 * done tickets lags behind the share of elapsed time; otherwise it is ON_TRACK.
 * Elapsed time runs from the start date, or from the creation date (UTC) when
 * there is no start date, to the target date. The caller passes today so that
 * every milestone of one response is judged on the same date.
 */
@Component
public class MilestoneHealthResolver {

    /**
     * Resolves the health of a milestone.
     *
     * @param milestone the milestone
     * @param done      attached tickets with status DONE
     * @param total     attached tickets
     * @param today     the current date in UTC
     * @return the derived health
     */
    public MilestoneHealth resolve(Milestone milestone, long done, long total, LocalDate today) {
        if (milestone.getStatus() == MilestoneStatus.CLOSED) {
            return MilestoneHealth.DONE;
        }

        LocalDate target = milestone.getTargetDate();
        if (target.isBefore(today)) {
            return MilestoneHealth.OVERDUE;
        }

        long daysLeft = ChronoUnit.DAYS.between(today, target);
        if (daysLeft > MilestoneConstant.AT_RISK_WINDOW_DAYS) {
            return MilestoneHealth.ON_TRACK;
        }

        double doneRatio = total == 0 ? 0d : (double) done / total;
        double elapsedRatio = elapsedRatio(resolveStart(milestone, today), target, today);
        return doneRatio < elapsedRatio ? MilestoneHealth.AT_RISK : MilestoneHealth.ON_TRACK;
    }

    /**
     * Start of the elapsed period: the start date, else the creation date in UTC.
     * A milestone that is not persisted yet has no creation date; today is used then.
     */
    private static LocalDate resolveStart(Milestone milestone, LocalDate today) {
        if (milestone.getStartDate() != null) {
            return milestone.getStartDate();
        }
        if (milestone.getCreatedAt() != null) {
            return milestone.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return today;
    }

    /**
     * Share of the period from start to target that has passed, clamped to 0..1.
     * A period without length (target on or before start) counts as fully elapsed.
     */
    private static double elapsedRatio(LocalDate start, LocalDate target, LocalDate today) {
        long span = ChronoUnit.DAYS.between(start, target);
        if (span <= 0) {
            return 1d;
        }
        double ratio = (double) ChronoUnit.DAYS.between(start, today) / span;
        return Math.max(0d, Math.min(1d, ratio));
    }
}
