package com.korur.oba.connect.routine.core.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.core.dto.RoutineFireResult;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;

/**
 * Fires hosted routines of any vendor. It knows nothing of who owns a routine or where its secret
 * is kept: the caller passes the routine id, the decrypted secret and the text.
 */
public interface RoutineService {

    /**
     * Tells whether a client is registered for a vendor, so its routines can be fired.
     *
     * @param provider the vendor
     * @return true when the vendor has a client
     */
    boolean isAvailable(RoutineProvider provider);

    /**
     * Checks a routine id against the vendor's format.
     *
     * @param provider  the vendor
     * @param routineId the routine id
     * @throws com.korur.oba.dojo.exception.internal.business.ValidationException with
     *         {@link com.korur.oba.connect.routine.core.exception.ExceptionDomainRoutineError#VAL_ROUTINE_ID_FORMAT}
     *         when the id does not match
     */
    void validateRoutineId(RoutineProvider provider, String routineId);

    /**
     * Fires a routine. A vendor failure is an answer, not an exception.
     *
     * @param provider  the vendor
     * @param routineId the routine to fire
     * @param secret    the routine's fire secret; never logged
     * @param text      the text the session receives
     * @return what happened
     */
    RoutineFireResult fire(RoutineProvider provider, String routineId, String secret, String text);
}
