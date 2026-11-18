package com.korur.oba.apps.kaya.render.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The kind a delivered path belongs to, which decides the table that stores it and the folder it is
 * rendered into. Every file below a skill directory is {@link #SKILL}; the skill folder mapping then
 * decides which part it is. {@link #SKILL_LINK} is the link .claude/skills/name, which is rendered from
 * the skill and never stored. {@link #TOOL_LINK} is one of the fixed links AGENTS.md, .claude/rules and
 * .claude/templates into .agents/, stored by name in the tool link domain. {@link #GUARDRAIL} lies below
 * the rules folder, in .agents/rules/guardrails/, but is its own kind and never a {@link #RULE}.
 */
public enum FileKind {
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
    SKILL_LINK,
    TOOL_LINK
}
