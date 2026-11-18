package com.korur.oba.apps.kaya.agentsfile.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the agents file controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class AgentsFileControllerLogConstant {

    private AgentsFileControllerLogConstant() {
    }

    public static final String AGENTS_FILE_LIST_REQUEST = "Received request to list the agents files of {} versions in organization {}";
    public static final String AGENTS_FILE_LIST_COMPLETED = "Listed {} agents files in organization {}";
    public static final String AGENTS_FILE_GET_REQUEST = "Received request to read agents file {} of organization {}";
    public static final String AGENTS_FILE_GET_COMPLETED = "Read agents file {} of organization {}";
}
