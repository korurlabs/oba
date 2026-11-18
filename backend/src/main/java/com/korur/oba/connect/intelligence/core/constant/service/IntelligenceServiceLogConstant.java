package com.korur.oba.connect.intelligence.core.constant.service;

/**
 * Service-layer log message constants for the intelligence layer.
 */
public final class IntelligenceServiceLogConstant {

    public static final String SENDING_CHAT_REQUEST = "Sending chat request to provider [{}] for use case [{}]";
    public static final String CHAT_REQUEST_COMPLETED = "Chat request completed for provider [{}]";
    public static final String RESOLVING_PROVIDER = "Resolving provider for use case [{}]";
    public static final String PROVIDER_RESOLVED = "Resolved provider [{}] for use case [{}]";
    public static final String CHECKING_AVAILABILITY = "Checking availability for provider [{}]";
    public static final String PROVIDER_AVAILABLE = "Provider [{}] is available";
    public static final String PROVIDER_NOT_AVAILABLE = "Provider [{}] is not available";

    private IntelligenceServiceLogConstant() {
    }
}
