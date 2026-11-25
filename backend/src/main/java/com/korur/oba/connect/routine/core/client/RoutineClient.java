package com.korur.oba.connect.routine.core.client;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;

/**
 * Fires the hosted routines of one vendor. Each vendor implements it once and exposes the
 * implementation as a bean; {@link com.korur.oba.connect.routine.core.router.RoutineRouter} finds it by
 * {@link #provider()}.
 */
public interface RoutineClient {

    /**
     * The vendor this client serves.
     *
     * @return the provider
     */
    RoutineProvider provider();

    /**
     * Tells whether this client calls the vendor at all. A switched-off client answers every fire
     * with {@link com.korur.oba.connect.routine.core.enums.RoutineFireStatus#FAILED} without a call.
     *
     * @return true when fires reach the vendor
     */
    boolean isEnabled();

    /**
     * Tells whether a routine id has the vendor's format.
     *
     * @param routineId the routine id
     * @return true when the id is well formed
     */
    boolean isValidRoutineId(String routineId);

    /**
     * Fires a routine. A vendor failure, a refusal or a timeout is an answer, not an exception.
     *
     * @param routineId the routine to fire
     * @param secret    the routine's fire secret; never logged
     * @param text      the text the session receives
     * @return what happened
     */
    RoutineFireResult fire(String routineId, String secret, String text);
}
