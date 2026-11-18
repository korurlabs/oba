package com.korur.oba.apps.kaya.seed.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Outcome of a seed run. PASSED means the run was applied and every repository it reached was
 * released; files it refused, links it left out and size warnings are in the report. FAILED means a
 * composition was refused, so nothing was kept, or the caller reported drift.
 */
public enum SeedRunStatus {
    PASSED,
    FAILED
}
