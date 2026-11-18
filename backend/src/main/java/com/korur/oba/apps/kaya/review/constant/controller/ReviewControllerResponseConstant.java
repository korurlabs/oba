package com.korur.oba.apps.kaya.review.constant.controller;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Response messages of the review controller.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReviewControllerResponseConstant {

    private ReviewControllerResponseConstant() {
    }

    public static final String RESPONSE_DRAFT_SUBMITTED = "Draft submitted for review successfully";
    public static final String RESPONSE_REVIEW_RECORDED = "Review recorded successfully";
    public static final String RESPONSE_CHANGES_RETRIEVED = "Changes retrieved successfully";
    public static final String RESPONSE_CHANGE_RETRIEVED = "Change retrieved successfully";
    public static final String RESPONSE_HISTORY_RETRIEVED = "File history retrieved successfully";
}
