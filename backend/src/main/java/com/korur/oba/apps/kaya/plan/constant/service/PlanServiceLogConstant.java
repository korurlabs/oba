package com.korur.oba.apps.kaya.plan.constant.service;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Log messages of the plan services.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PlanServiceLogConstant {

    private PlanServiceLogConstant() {
    }

    // --- Ingest ---
    public static final String STORING_PLANS = "Storing {} plans of session {}";
    public static final String PLAN_CREATED = "Created {} plan {} of session {} in repository {}";
    public static final String PLAN_VERSION_ADDED = "Added version {} ({}) to plan {} from session {}; review reopened";
    public static final String PLAN_UNCHANGED = "Plan {} is unchanged at version {}; capture of session {} skipped";
    public static final String PLAN_CAPTURE_STALE = "Plan {} capture of session {} at {} is older than version {} at {}; skipped";
    public static final String PLAN_OF_ANOTHER_USER_SKIPPED = "Plan {} belongs to user {}; agent session {} leaves it unchanged";
    public static final String PLAN_FEATURE_LINKED = "Linked plan {} to feature {} from planning handoff of session {}";
    public static final String PLAN_FEATURE_TOO_LONG = "Feature directory of handoff {} of session {} exceeds {} characters; not linked";
    public static final String PLAN_TITLE_TRUNCATED = "Cut a plan title of session {} to {} characters";
    public static final String SHA256_UNAVAILABLE = "The runtime provides no SHA-256 digest";

    // --- Member reads ---
    public static final String LISTING_PLANS = "Listing plans of organization {} with {}";
    public static final String READING_FILTERS = "Reading plan filter options of organization {}";
    public static final String READING_PLAN = "Reading plan {} of organization {} for user {}";
    public static final String READING_VERSION = "Reading version {} of plan {} of organization {}";
    public static final String READING_DIFF = "Reading the diff of version {} of plan {} of organization {}";
    public static final String PLAN_NOT_FOUND = "Plan {} not found in organization {}";
    public static final String VERSION_NOT_FOUND = "Version {} of plan {} not found";
    public static final String FEEDBACK_NOT_FOUND = "Feedback {} of plan {} not found";

    // --- Feedback ---
    public static final String FEEDBACK_COMMENT_MISSING = "{} on plan {} by user {} has no comment";
    public static final String FEEDBACK_LINES_INVALID = "Feedback on plan {} version {} by user {} has lines {} to {}, the version has {}";
    public static final String FEEDBACK_VERSION_NOT_LATEST = "{} on plan {} version {} by user {} refused; the latest version is {}";
    public static final String FEEDBACK_RECORDED = "User {} gave {} on plan {} version {}; review state {}";
    public static final String FEEDBACK_NOT_OWN = "User {} may not withdraw feedback {} of user {} on plan {}";
    public static final String FEEDBACK_ALREADY_DELIVERED = "Feedback {} on plan {} was delivered to session {}; withdraw by user {} refused";
    public static final String FEEDBACK_WITHDRAWN = "User {} withdrew feedback {} on plan {}; review state {}";

    // --- Machine delivery ---
    public static final String READING_PENDING_FOR_SESSIONS = "Reading pending plan feedback of {} sessions for user {} of organization {}";
    public static final String READING_PENDING_FOR_BRANCH = "Reading pending plan feedback of a branch for user {} of organization {}";
    public static final String PENDING_REPOSITORY_UNKNOWN = "No repository of organization {} has the remote of the request, or it is not a remote; no pending plan feedback";
    public static final String PENDING_RESOLVED = "Found {} pending plan feedback for user {} of organization {}";
    public static final String DELIVERY_QUERY_INVALID = "Pending plan feedback request of machine {} names {} session ids, remote given {}, branch given {}";
    public static final String ACKNOWLEDGING_FEEDBACK = "Acknowledging {} plan feedback for session {} of user {} in organization {}";
    public static final String FEEDBACK_ACKNOWLEDGED = "Marked {} plan feedback of plan {} delivered to session {}; {} still open";

    // --- Pack matching ---
    public static final String PLAN_PACK_UNMATCHED = "Plans directory {} names no pack that repository {} uses; spec of session {} stored without a pack";
}
