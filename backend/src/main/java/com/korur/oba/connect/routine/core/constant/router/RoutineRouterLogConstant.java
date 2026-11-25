package com.korur.oba.connect.routine.core.constant.router;

/**
 * Log messages of the routine router.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RoutineRouterLogConstant {

    private RoutineRouterLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String CLIENTS_REGISTERED = "Routine clients registered for providers {}";
    public static final String CLIENT_MISSING = "No routine client is registered for provider [{}]";
    public static final String CLIENT_DUPLICATE = "Routine clients {} and {} both serve provider [{}]";
}
