package com.korur.oba.apps.kaya.render.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * When an AI tool loads a path it reads, so that a reader knows whether the file shapes every session
 * or only some.
 *
 * - ALWAYS: at the start of every session.
 * - ON_DEMAND: when the tool uses it, such as a skill it picks, a subagent it runs, or a
 *   folder's AGENTS.md once it works below that folder.
 * - ON_REFERENCE: only when another file it read names the path, such as AGENTS.md naming a rule or
 *   the settings registering a hook.
 */
public enum ToolLoading {
    ALWAYS,
    ON_DEMAND,
    ON_REFERENCE
}
