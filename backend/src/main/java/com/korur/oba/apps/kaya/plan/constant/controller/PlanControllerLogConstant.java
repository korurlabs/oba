package com.korur.oba.apps.kaya.plan.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the plan controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PlanControllerLogConstant {

    private PlanControllerLogConstant() {
    }

    public static final String PLAN_LIST_REQUEST = "Request for the plans of organization {}";
    public static final String PLAN_LIST_COMPLETED = "Returned {} plans of organization {}";
    public static final String FILTERS_REQUEST = "Request for the plan filter options of organization {}";
    public static final String FILTERS_COMPLETED = "Returned the plan filter options of organization {}";
    public static final String PLAN_GET_REQUEST = "Request of user {} for plan {} in organization {}";
    public static final String PLAN_GET_COMPLETED = "Returned plan {} with {} versions and {} feedback in organization {}";
    public static final String VERSION_GET_REQUEST = "Request for version {} of plan {} in organization {}";
    public static final String VERSION_GET_COMPLETED = "Returned version {} of plan {} in organization {}";
    public static final String DIFF_GET_REQUEST = "Request for the diff of version {} of plan {} in organization {}";
    public static final String DIFF_GET_COMPLETED = "Returned the diff of version {} of plan {} in organization {}";
    public static final String FEEDBACK_CREATE_REQUEST = "Request of user {} to give {} on plan {} version {} in organization {}";
    public static final String FEEDBACK_CREATE_COMPLETED = "Recorded feedback {} on plan {} in organization {}";
    public static final String FEEDBACK_DELETE_REQUEST = "Request of user {} to withdraw feedback {} on plan {} in organization {}";
    public static final String FEEDBACK_DELETE_COMPLETED = "Withdrew feedback {} on plan {} in organization {}";

    // --- Machine delivery ---
    public static final String PENDING_REQUEST = "Request of machine {} for pending plan feedback";
    public static final String PENDING_COMPLETED = "Returned {} pending plan feedback to machine {}";
    public static final String ACKS_REQUEST = "Request of machine {} to acknowledge {} plan feedback for session {}";
    public static final String ACKS_COMPLETED = "Acknowledged {} plan feedback for session {} of machine {}";
}
