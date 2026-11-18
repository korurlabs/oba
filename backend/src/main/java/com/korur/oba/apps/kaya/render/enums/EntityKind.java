package com.korur.oba.apps.kaya.render.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The kind of entity a delivered file is stored as. It follows {@link FileKind}, except that a file
 * below a skill directory is the {@link #SKILL} itself for its SKILL.md and otherwise the part the
 * skill folder mapping gives it, and the link of a skill is no entity.
 */
public enum EntityKind {
    AGENTS_FILE,
    RULE,
    GUARDRAIL,
    CONVENTION,
    ROLE,
    WORKFLOW,
    TEMPLATE,
    HOOK,
    SUBAGENT,
    SETTINGS,
    SKILL,
    SCRIPT,
    REFERENCE,
    TOOL_PARAMETER
}
