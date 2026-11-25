package com.korur.oba.connect.routine.core.constant.service;

/**
 * Log messages of the routine service.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RoutineServiceLogConstant {

    private RoutineServiceLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String ROUTINE_ID_INVALID = "Routine id refused for provider [{}]: wrong format";
    public static final String FIRING_ROUTINE = "Firing routine [{}] of provider [{}]";
    public static final String ROUTINE_FIRE_ANSWERED = "Routine [{}] of provider [{}] answered {} (HTTP {})";
}
