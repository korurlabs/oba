package com.korur.oba.apps.kaya.session.enums;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK

// 4. Internal

/**
 * How a skill came to be used in a session: a Skill tool call, a slash command the user typed, or the preload list of an agent file.
 */
public enum SessionSkillUseSource {
    TOOL,
    COMMAND,
    PRELOADED
}
