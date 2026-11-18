package com.korur.oba.apps.kaya.review.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the review services.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReviewServiceLogConstant {

    private ReviewServiceLogConstant() {
    }

    // --- Submit ---
    public static final String SUBMITTING = "User {} submits draft {} in organization {}";
    public static final String SUBMIT_REFUSED = "Draft {} breaks {} composition rules of its repositories; submit refused";
    public static final String SUBMITTED = "Draft {} of owner {} is in review, executable change {}";

    // --- Review ---
    public static final String REVIEW_NOT_IN_REVIEW = "Version {} is {}, not in review; review by user {} refused";
    public static final String REVIEW_COMMENT_MISSING = "Review {} of version {} by user {} has no comment";
    public static final String REVIEW_BY_PROPOSER = "User {} proposed version {} and may not review it";
    public static final String REVIEW_ROLE_TOO_LOW = "User {} with role {} may not review version {}, which needs {}";
    public static final String REVIEW_RECORDED = "User {} recorded {} on version {} of owner {}";
    public static final String VERSION_APPROVED = "Approved version {} of owner {}, released to {} repositories";

    // --- Changes ---
    public static final String LISTING_CHANGES = "Listing changes of organization {}, pack {}, status {}";
    public static final String READING_HISTORY = "Reading the history of {} from version {} of organization {} for user {}";
    public static final String READING_CHANGE = "Reading change {} of organization {} for user {}";
}
