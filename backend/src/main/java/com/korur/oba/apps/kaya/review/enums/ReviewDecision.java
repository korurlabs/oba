package com.korur.oba.apps.kaya.review.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * What a reviewer does with a version in review: APPROVE releases it, REJECT sends it back to its
 * proposer as a draft, COMMENT leaves it in review.
 */
public enum ReviewDecision {
    APPROVE,
    REJECT,
    COMMENT
}
