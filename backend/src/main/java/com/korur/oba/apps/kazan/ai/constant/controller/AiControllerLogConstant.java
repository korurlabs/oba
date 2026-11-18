package com.korur.oba.apps.kazan.ai.constant.controller;

/**
 * Log message constants for AI controller operations.
 */
public final class AiControllerLogConstant {

    private AiControllerLogConstant() {
        // Private constructor to prevent instantiation
    }

    public static final String AI_AVAILABLE_REQUEST = "Received request to check AI availability";
    public static final String AI_AVAILABLE_COMPLETED = "Check AI availability request completed";

    public static final String AI_ANALYZE_REQUEST = "Received request to analyze ticket";
    public static final String AI_ANALYZE_COMPLETED = "Analyze ticket request completed";

    public static final String AI_CREATE_REQUEST = "Received request to create tickets from suggestions";
    public static final String AI_CREATE_COMPLETED = "Create tickets from suggestions request completed";
}
