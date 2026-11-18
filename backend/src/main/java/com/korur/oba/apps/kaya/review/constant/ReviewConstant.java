package com.korur.oba.apps.kaya.review.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Field names, limits and messages of reviews and the changes list.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class ReviewConstant {

    private ReviewConstant() {
    }

    // --- Error value keys ---
    public static final String FIELD_VERSION_ID = "versionId";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_COMMENT = "comment";
    public static final String FIELD_DECISION = "decision";
    public static final String FIELD_REQUIRED_ROLE = "requiredRole";
    public static final String FIELD_REASON = "reason";

    // --- Request limits ---
    public static final int COMMENT_MAX_LENGTH = 2000;
    public static final String DECISION_REQUIRED_MESSAGE = "A decision is required";
    public static final String COMMENT_SIZE_MESSAGE = "The comment must be 1 to 2000 characters";
    public static final String COMMENT_REQUIRED_MESSAGE = "A rejection or a comment needs a comment";
    public static final String VERSION_ID_REQUIRED_MESSAGE = "A version id is required";
    public static final String PATH_REQUIRED_MESSAGE = "A path is required";

    // --- Refusal reasons (errorValues.reason of KAY_CFG_004) ---
    public static final String REASON_PROPOSER = "PROPOSER";
    public static final String REASON_ROLE = "ROLE";
}
