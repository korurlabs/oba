package com.korur.oba.apps.kaya.template.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the template services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class TemplateServiceLogConstant {

    private TemplateServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_TEMPLATES = "Listing templates of organization {} with {}";
    public static final String READING_TEMPLATE = "Reading template {} of organization {}";
    public static final String LOADING_VERSION_TEMPLATES = "Loading the templates of version {}";
    public static final String TEMPLATE_NOT_FOUND = "Template {} not found in organization {}";

    // --- Writes ---
    public static final String TEMPLATES_REPLACED = "Replaced the templates of version {} in organization {}: {} removed, {} written";
    public static final String TEMPLATE_SAVED = "Saved the template {} of version {} in organization {}: {} old row removed";
    public static final String TEMPLATE_DELETED = "Deleted the template {} of version {} in organization {}: {} row removed";
}
