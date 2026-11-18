package com.korur.oba.apps.kaya.plan.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Where a version was captured: FILE is a spec file written in the repository, PLAN_MODE the plan
 * of an ExitPlanMode call, MESSAGE a final assistant message that has the shape of a plan.
 */
public enum PlanVersionSource {
    FILE,
    PLAN_MODE,
    MESSAGE
}
