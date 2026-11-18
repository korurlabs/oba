package com.korur.oba.apps.kaya.workflow.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the workflow services, including the not-found lines.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class WorkflowServiceLogConstant {

    private WorkflowServiceLogConstant() {
    }

    // --- Reads ---
    public static final String LISTING_WORKFLOWS = "Listing workflows of organization {} with {}";
    public static final String READING_WORKFLOW = "Reading workflow {} of organization {}";
    public static final String LOADING_VERSION_WORKFLOWS = "Loading the workflows of version {}";
    public static final String WORKFLOW_NOT_FOUND = "Workflow {} not found in organization {}";

    // --- Writes ---
    public static final String WORKFLOWS_REPLACED = "Replaced the workflows of version {} in organization {}: {} removed, {} written";
    public static final String WORKFLOW_SAVED = "Saved the workflow {} of version {} in organization {}: {} old row removed";
    public static final String WORKFLOW_DELETED = "Deleted the workflow {} of version {} in organization {}: {} row removed";
    public static final String WORKFLOW_PART_SAVED = "Saved the workflow part {} of version {} in organization {}";
    public static final String WORKFLOW_PARTS_DELETED = "Deleted {} unlinked workflow parts of version {} in organization {}";
}
