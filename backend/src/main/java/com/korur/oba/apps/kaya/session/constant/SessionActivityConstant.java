package com.korur.oba.apps.kaya.session.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Set;

// 4. Internal

/**
 * The names the activity derivation reads from the recorded hook events, as the recorder writes
 * them and as the frontend reads them, so the stored subagents and skill uses match the session page.
 */
public final class SessionActivityConstant {

    // Event keys, as the recorder writes them.
    public static final String KEY_AT = "at";
    public static final String KEY_EVENT = "event";
    public static final String KEY_AGENT_ID = "agent_id";
    public static final String KEY_AGENT_TYPE = "agent_type";
    public static final String KEY_COMMAND_NAME = "command_name";
    public static final String KEY_TOOL_NAME = "tool_name";
    public static final String KEY_TOOL_USE_ID = "tool_use_id";
    public static final String KEY_SKILL = "skill";
    public static final String KEY_SUBAGENT_TYPE = "subagent_type";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_OK = "ok";
    public static final String KEY_SPAWNED_AGENT_ID = "spawned_agent_id";
    public static final String KEY_MODEL = "model";

    // Hook event names.
    public static final String EVENT_SESSION_START = "SessionStart";
    public static final String EVENT_USER_PROMPT_SUBMIT = "UserPromptSubmit";
    public static final String EVENT_USER_PROMPT_EXPANSION = "UserPromptExpansion";
    public static final String EVENT_PRE_TOOL_USE = "PreToolUse";
    public static final String EVENT_POST_TOOL_USE = "PostToolUse";
    public static final String EVENT_POST_TOOL_USE_FAILURE = "PostToolUseFailure";
    public static final String EVENT_SUBAGENT_START = "SubagentStart";
    public static final String EVENT_SUBAGENT_STOP = "SubagentStop";
    public static final String EVENT_STOP = "Stop";
    public static final String EVENT_SESSION_END = "SessionEnd";
    public static final Set<String> HOOK_EVENTS = Set.of(
            EVENT_SESSION_START, EVENT_USER_PROMPT_SUBMIT, EVENT_USER_PROMPT_EXPANSION, EVENT_PRE_TOOL_USE,
            EVENT_POST_TOOL_USE, EVENT_POST_TOOL_USE_FAILURE, EVENT_SUBAGENT_START, EVENT_SUBAGENT_STOP,
            EVENT_STOP, EVENT_SESSION_END);

    // Tools.
    public static final Set<String> AGENT_TOOLS = Set.of("Agent", "Task");
    public static final String TOOL_SKILL = "Skill";
    public static final String UNKNOWN_SUBAGENT_TYPE = "unknown";

    // A skill use is counted per source and name.
    public static final String SKILL_KEY_SEPARATOR = ":";

    private SessionActivityConstant() {
    }
}
