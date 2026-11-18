package com.korur.oba.apps.kaya.plan.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * What a reviewer gives on a version: APPROVE approves the plan, REQUEST_CHANGES asks the session to
 * revise it, COMMENT leaves the review state as it is. Only APPROVE may come without a comment.
 */
public enum PlanFeedbackDecision {
    APPROVE,
    REQUEST_CHANGES,
    COMMENT
}
