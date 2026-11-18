package com.korur.oba.apps.kaya.guardrail.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the guardrail controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class GuardrailControllerLogConstant {

    private GuardrailControllerLogConstant() {
    }

    public static final String GUARDRAIL_LIST_REQUEST = "Received request to list the guardrails of {} versions in organization {}";
    public static final String GUARDRAIL_LIST_COMPLETED = "Listed {} guardrails in organization {}";
    public static final String GUARDRAIL_GET_REQUEST = "Received request to read guardrail {} of organization {}";
    public static final String GUARDRAIL_GET_COMPLETED = "Read guardrail {} of organization {}";
}
