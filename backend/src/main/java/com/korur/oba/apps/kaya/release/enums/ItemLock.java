package com.korur.oba.apps.kaya.release.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Why an inherited item cannot be overridden by a pack: an ancestor that gives it is required, so
 * its items set limits for every repository, or the item is a guardrail, which limits every session.
 */
public enum ItemLock {
    REQUIRED,
    GUARDRAIL
}
