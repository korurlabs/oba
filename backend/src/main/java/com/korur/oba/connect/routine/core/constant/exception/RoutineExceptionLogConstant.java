package com.korur.oba.connect.routine.core.constant.exception;

/**
 * Log messages of refused routine input.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RoutineExceptionLogConstant {

    private RoutineExceptionLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String ROUTINE_ID_INVALID = "Routine id refused for provider [{}]: wrong format";
}
