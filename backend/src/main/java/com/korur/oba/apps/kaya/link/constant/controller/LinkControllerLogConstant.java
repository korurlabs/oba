package com.korur.oba.apps.kaya.link.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Controller log messages of the links between delivered Markdown files.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LinkControllerLogConstant {

    private LinkControllerLogConstant() {
    }

    public static final String LINKS_REQUEST = "Received request to read links of version {} and repository {} of organization {}";
    public static final String DEPENDENCIES_REQUEST = "Received request to read the dependencies of {} in versions {} of organization {}";
    public static final String DEPENDENCIES_COMPLETED = "Read {} dependencies and {} users of {} in organization {}";
    public static final String LINKS_COMPLETED = "Read {} links of version {} and repository {} of organization {}";
}
