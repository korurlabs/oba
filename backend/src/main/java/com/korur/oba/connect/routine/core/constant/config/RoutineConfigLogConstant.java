package com.korur.oba.connect.routine.core.constant.config;

/**
 * Log messages of the routine connector's configuration.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RoutineConfigLogConstant {

    private RoutineConfigLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String EXECUTOR_REJECTED =
            "Routine fire dropped, the routine executor queue is full: queueCapacity={}";
}
