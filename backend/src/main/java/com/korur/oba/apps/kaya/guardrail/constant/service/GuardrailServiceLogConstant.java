package com.korur.oba.apps.kaya.guardrail.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the guardrail services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class GuardrailServiceLogConstant {

    private GuardrailServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_GUARDRAILS = "Listing guardrails of organization {} with {}";
    public static final String READING_GUARDRAIL = "Reading guardrail {} of organization {}";
    public static final String LOADING_VERSION_GUARDRAILS = "Loading the guardrails of version {}";
    public static final String GUARDRAIL_NOT_FOUND = "Guardrail {} not found in organization {}";

    // --- Writes ---
    public static final String GUARDRAILS_REPLACED = "Replaced the guardrails of version {} in organization {}: {} removed, {} written";
    public static final String GUARDRAIL_SAVED = "Saved the guardrail {} of version {} in organization {}: {} old row removed";
    public static final String GUARDRAIL_DELETED = "Deleted the guardrail {} of version {} in organization {}: {} row removed";
}
