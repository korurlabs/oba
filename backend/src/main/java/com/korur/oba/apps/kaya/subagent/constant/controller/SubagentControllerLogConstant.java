package com.korur.oba.apps.kaya.subagent.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the subagent controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class SubagentControllerLogConstant {

    private SubagentControllerLogConstant() {
    }

    public static final String SUBAGENT_LIST_REQUEST = "Received request to list the subagents of {} versions in organization {}";
    public static final String SUBAGENT_LIST_COMPLETED = "Listed {} subagents in organization {}";
    public static final String SUBAGENT_GET_REQUEST = "Received request to read subagent {} of organization {}";
    public static final String SUBAGENT_GET_COMPLETED = "Read subagent {} of organization {}";
}
