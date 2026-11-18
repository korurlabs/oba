package com.korur.oba.apps.kazan.ai.constant.service;

/**
 * Log message constants for AI service operations.
 */
public final class AiServiceLogConstant {

    private AiServiceLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String AI_ANALYZE_STARTED = "AI analysis started for board: {}";
    public static final String AI_ANALYZE_COMPLETED = "AI analysis completed for board: {}";
    public static final String AI_CREATE_FROM_SUGGESTIONS = "Creating {} tickets from AI suggestions for board: {}";
    public static final String AI_CREATE_FROM_SUGGESTIONS_COMPLETED = "Created {} tickets from AI suggestions for board: {}";
}
