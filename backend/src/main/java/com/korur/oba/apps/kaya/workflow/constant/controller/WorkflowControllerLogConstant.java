package com.korur.oba.apps.kaya.workflow.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the workflow controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class WorkflowControllerLogConstant {

    private WorkflowControllerLogConstant() {
    }

    public static final String WORKFLOW_LIST_REQUEST = "Received request to list the workflows of {} versions in organization {}";
    public static final String WORKFLOW_LIST_COMPLETED = "Listed {} workflows in organization {}";
    public static final String WORKFLOW_GET_REQUEST = "Received request to read workflow {} of organization {}";
    public static final String WORKFLOW_GET_COMPLETED = "Read workflow {} of organization {}";
}
