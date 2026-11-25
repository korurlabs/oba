package com.korur.oba.connect.routine.anthropic.constant.client;

/**
 * Log messages of the Anthropic routine client.
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AnthropicRoutineClientLogConstant {

    private AnthropicRoutineClientLogConstant() {
        throw new UnsupportedOperationException("Constant class should not be instantiated");
    }

    public static final String FIRE_SKIPPED_DISABLED =
            "Anthropic routine [{}] not fired: oba.integrations.routine.anthropic.enabled is false";
    public static final String FIRE_SKIPPED_INVALID_ID = "Anthropic routine not fired: the routine id has the wrong format";
    public static final String FIRE_ACCEPTED = "Anthropic routine [{}] fired, session [{}]";
    public static final String FIRE_REFUSED = "Anthropic routine [{}] fire refused: provider ANTHROPIC, HTTP {}, status {}";
    public static final String FIRE_NO_ANSWER = "Anthropic routine [{}] fire got no answer: provider ANTHROPIC";
}
