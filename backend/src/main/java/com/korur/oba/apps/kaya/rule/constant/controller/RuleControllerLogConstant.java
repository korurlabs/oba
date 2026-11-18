package com.korur.oba.apps.kaya.rule.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the rule controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RuleControllerLogConstant {

    private RuleControllerLogConstant() {
    }

    public static final String RULE_LIST_REQUEST = "Received request to list the rules of {} versions in organization {}";
    public static final String RULE_LIST_COMPLETED = "Listed {} rules in organization {}";
    public static final String RULE_GET_REQUEST = "Received request to read rule {} of organization {}";
    public static final String RULE_GET_COMPLETED = "Read rule {} of organization {}";
}
