package com.korur.oba.apps.kaya.validation.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Where a rule's paths pattern, with its brace groups expanded, lies against a project root. Only
 * INSIDE patterns may be taken over by a project; a SPANNING pattern matches files under the root
 * and elsewhere, so removing it would also drop the rule outside the project.
 */
public enum PatternClass {
    INSIDE,
    OUTSIDE,
    SPANNING
}
