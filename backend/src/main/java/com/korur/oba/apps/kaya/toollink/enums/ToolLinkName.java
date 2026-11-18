package com.korur.oba.apps.kaya.toollink.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * The links an AI tool reads to find the tool-neutral source under .agents/. Each name stands for
 * one fixed path and target, which the render domain keeps; a version holds each link at most once.
 */
public enum ToolLinkName {
    // AGENTS.md at the repository root, a link to .agents/AGENTS.md.
    ROOT_AGENTS_FILE,
    // .claude/rules, a link to ../.agents/rules, through which Claude Code loads the shared rules.
    CLAUDE_RULES,
    // .claude/templates, a link to ../.agents/templates.
    CLAUDE_TEMPLATES
}
