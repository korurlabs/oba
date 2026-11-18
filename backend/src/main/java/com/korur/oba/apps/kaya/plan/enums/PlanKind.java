package com.korur.oba.apps.kaya.plan.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * What a plan is: SPEC is a spec file of a repository, identified by its repository-relative path and
 * shared by every session that edits it; SESSION is the plan of one session, at most one per session.
 */
public enum PlanKind {
    SPEC,
    SESSION
}
