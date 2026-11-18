package com.korur.oba.apps.kaya.review.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the review controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReviewControllerLogConstant {

    private ReviewControllerLogConstant() {
    }

    public static final String SUBMIT_REQUEST = "Request of user {} to submit draft {} in organization {}";
    public static final String SUBMIT_COMPLETED = "Submitted draft {} in organization {}";
    public static final String REVIEW_REQUEST = "Request of user {} to review version {} with {} in organization {}";
    public static final String REVIEW_COMPLETED = "Reviewed version {}, now {}, {} releases, in organization {}";
    public static final String CHANGES_REQUEST = "Request for the changes of organization {}, pack {}, status {}";
    public static final String CHANGES_COMPLETED = "Returned {} changes of organization {}";
    public static final String HISTORY_REQUEST = "Request of user {} for the history of {} from version {} in organization {}";
    public static final String HISTORY_COMPLETED = "Returned {} versions in the history of {} in organization {}";
    public static final String CHANGE_REQUEST = "Request of user {} for change {} in organization {}";
    public static final String CHANGE_COMPLETED = "Returned change {} with {} reviews in organization {}";
}
