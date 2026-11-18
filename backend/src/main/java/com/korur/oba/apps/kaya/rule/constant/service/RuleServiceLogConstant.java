package com.korur.oba.apps.kaya.rule.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the rule services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RuleServiceLogConstant {

    private RuleServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_RULES = "Listing rules of organization {} with {}";
    public static final String READING_RULE = "Reading rule {} of organization {}";
    public static final String LOADING_VERSION_RULES = "Loading the rules of version {}";
    public static final String LOADING_CITING_RULES = "Loading the rules citing {} in {} versions";
    public static final String RULE_NOT_FOUND = "Rule {} not found in organization {}";

    // --- Writes ---
    public static final String RULES_REPLACED = "Replaced the rules of version {} in organization {}: {} removed, {} written";
    public static final String RULE_SAVED = "Saved the rule {} of version {} in organization {}: {} old row removed";
    public static final String RULE_DELETED = "Deleted the rule {} of version {} in organization {}: {} row removed";
}
