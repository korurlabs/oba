package com.korur.oba.apps.kaya.template.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the template controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class TemplateControllerLogConstant {

    private TemplateControllerLogConstant() {
    }

    public static final String TEMPLATE_LIST_REQUEST = "Received request to list the templates of {} versions in organization {}";
    public static final String TEMPLATE_LIST_COMPLETED = "Listed {} templates in organization {}";
    public static final String TEMPLATE_GET_REQUEST = "Received request to read template {} of organization {}";
    public static final String TEMPLATE_GET_COMPLETED = "Read template {} of organization {}";
}
