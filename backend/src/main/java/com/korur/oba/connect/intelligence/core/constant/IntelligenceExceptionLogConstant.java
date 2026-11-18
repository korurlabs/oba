package com.korur.oba.connect.intelligence.core.constant;

/**
 * Exception-related log message constants for the intelligence layer.
 */
public final class IntelligenceExceptionLogConstant {

    public static final String PROVIDER_UNAVAILABLE = "Intelligence provider [{}] is not available";
    public static final String PROVIDER_REQUEST_FAILED = "Intelligence provider [{}] request failed";
    public static final String PROVIDER_EMPTY_RESPONSE = "Intelligence provider [{}] returned empty response";
    public static final String NO_PROVIDER_FOR_USE_CASE = "No provider configured for use case [{}]";
    public static final String CHAT_CLIENT_NOT_FOUND = "No ChatClient found for provider [{}]";

    private IntelligenceExceptionLogConstant() {
    }
}
