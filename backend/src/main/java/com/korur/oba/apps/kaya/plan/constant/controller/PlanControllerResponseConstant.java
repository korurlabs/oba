package com.korur.oba.apps.kaya.plan.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the plan controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PlanControllerResponseConstant {

    private PlanControllerResponseConstant() {
    }

    public static final String RESPONSE_PLANS_RETRIEVED = "Plans retrieved successfully";
    public static final String RESPONSE_FILTERS_RETRIEVED = "Plan filters retrieved successfully";
    public static final String RESPONSE_PLAN_RETRIEVED = "Plan retrieved successfully";
    public static final String RESPONSE_VERSION_RETRIEVED = "Plan version retrieved successfully";
    public static final String RESPONSE_DIFF_RETRIEVED = "Plan version diff retrieved successfully";
    public static final String RESPONSE_FEEDBACK_CREATED = "Plan feedback recorded successfully";
    public static final String RESPONSE_FEEDBACK_WITHDRAWN = "Plan feedback withdrawn successfully";
    public static final String RESPONSE_PENDING_RETRIEVED = "Pending plan feedback retrieved successfully";
    public static final String RESPONSE_FEEDBACK_ACKNOWLEDGED = "Plan feedback acknowledged successfully";
}
