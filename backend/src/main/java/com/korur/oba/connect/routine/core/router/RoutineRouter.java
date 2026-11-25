package com.korur.oba.connect.routine.core.router;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.connect.routine.core.client.RoutineClient;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;

/**
 * Resolves a vendor to the client that fires its routines.
 */
public interface RoutineRouter {

    /**
     * Resolves the client of a vendor. Every {@link RoutineProvider} value has exactly one.
     *
     * @param provider the vendor
     * @return its client
     */
    RoutineClient resolveClient(RoutineProvider provider);
}
