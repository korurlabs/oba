package com.korur.oba.connect.routine.core.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.core.enums.RoutineFireStatus;

/**
 * What happened when a hosted routine was fired.
 *
 * @param status     the outcome
 * @param httpStatus the vendor's HTTP status, or null when no answer came (timeout, connector off)
 * @param sessionUrl the link to the session the vendor started, or null when none started
 */
public record RoutineFireResult(RoutineFireStatus status, Integer httpStatus, String sessionUrl) {

    /**
     * A failure that got no answer from the vendor.
     *
     * @return a {@link RoutineFireStatus#FAILED} result without HTTP status or session
     */
    public static RoutineFireResult failedWithoutAnswer() {
        return new RoutineFireResult(RoutineFireStatus.FAILED, null, null);
    }
}
