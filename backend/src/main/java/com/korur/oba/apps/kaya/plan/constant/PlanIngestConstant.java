package com.korur.oba.apps.kaya.plan.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * Patterns and values the plan ingest reads plans and planning handoffs with.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class PlanIngestConstant {

    private PlanIngestConstant() {
    }

    // --- Hashing ---
    public static final String SHA256_ALGORITHM = "SHA-256";

    // --- Paths ---
    public static final String PATH_SEPARATOR = "/";
    // The directory right after a plans/ segment, which names the folder a spec is about.
    public static final String PLANS_DIRECTORY_PATTERN = "(?:^|/)plans/([^/]+)/";
    // A planning handoff, as a path below handoffs/: the feature directory, then planning.md.
    public static final String PLANNING_HANDOFF_PATTERN = "^([^/]+)/planning\\.md$";
    // A spec path named in a handoff's text; the match starts at docs/, so an absolute or worktree path also counts.
    public static final String SPEC_PATH_IN_TEXT_PATTERN = "(?<![A-Za-z0-9_.-])(docs/[A-Za-z0-9_./-]+?\\.md)(?![A-Za-z0-9_./-])";
    // Distinct spec paths read from one handoff; a planning handoff names a handful.
    public static final int SPEC_PATHS_IN_TEXT_MAX = 20;

    // --- Titles ---
    public static final String HEADING_ONE_PATTERN = "^#\\s+(.+?)\\s*#*\\s*$";
    // A Goal line: "Goal: x", "**Goal**: x", "1. **Goal:** x" or a "## Goal" heading with the text on a later line.
    public static final String GOAL_LINE_PATTERN = "^\\s*(?:\\d+\\.\\s+)?(?:#{1,6}\\s+(?:\\*\\*)?Goal\\b(?:\\*\\*)?\\s*:?|(?:\\*\\*)?Goal\\b(?:\\*\\*)?\\s*:(?:\\*\\*)?)\\s*(.*?)\\s*$";
    public static final String SESSION_PLAN_TITLE_FALLBACK = "Session plan";
}
