package com.korur.oba.apps.kaya.plan.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Where the review of a plan stands. A new version returns a plan to OPEN; APPROVED and
 * CHANGES_REQUESTED follow the latest deciding feedback on the latest version.
 */
public enum PlanReviewState {
    OPEN,
    APPROVED,
    CHANGES_REQUESTED
}
