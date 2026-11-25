package com.korur.oba.connect.routine.anthropic.constant.config;

/**
 * Log messages of the Anthropic routine configuration.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AnthropicRoutineConfigLogConstant {

    private AnthropicRoutineConfigLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String ROUTINES_ENABLED = "Anthropic routine fires are on: base URL {}";
    public static final String ROUTINES_DISABLED =
            "Anthropic routine fires are off: oba.integrations.routine.anthropic.enabled is false";
}
