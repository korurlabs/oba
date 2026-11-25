package com.korur.oba.connect.routine.core.constant;

/**
 * Field names and executor settings of the routine connector.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RoutineConstant {

    private RoutineConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    // --- Error values ---
    public static final String FIELD_PROVIDER = "provider";

    // --- Fire executor ---
    // Callers run fires on this executor with @Async, so a vendor call never holds a request thread.
    public static final String EXECUTOR_BEAN_NAME = "routineExecutor";
    public static final String EXECUTOR_THREAD_PREFIX = "routine-";
}
