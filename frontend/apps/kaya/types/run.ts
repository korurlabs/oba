/**
 * The recorder's data model: the hook events Kaya's API returns for a session
 * (RunEvent), the status of a handoff snapshot, the skills catalogue the
 * timeline names preloaded skills from, and the permission rules the
 * allowlist lists.
 *
 * Field names are snake_case because they are the recorder's JSON keys, read
 * as they are.
 */

/**
 * The `hook_event_name` values the recorder is registered for. A failed tool
 * call fires `PostToolUseFailure` instead of `PostToolUse`, so both end a call.
 */
export type HookEventName =
  | "SessionStart"
  | "UserPromptSubmit"
  | "UserPromptExpansion"
  | "PreToolUse"
  | "PostToolUse"
  | "PostToolUseFailure"
  | "SubagentStart"
  | "SubagentStop"
  | "Stop"
  | "SessionEnd";

/**
 * One line of `events.jsonl`. Only `at`, `event` and `cwd` are always present;
 * every other field is set by the events that carry it.
 */
export type RunEvent = {
  /** The event's number in its session, from 1, as the server stored it; absent in fixtures. */
  seq?: number;
  /** ISO 8601 UTC, to the second, for example "2026-10-22T14:05:09Z". */
  at: string;
  event: HookEventName;
  /**
   * The subagent the hook fired in; absent when it fired in the session. On
   * SubagentStart and SubagentStop they name the subagent itself.
   */
  agent_id?: string;
  agent_type?: string;
  cwd: string;
  /** SessionStart: "startup", "resume", "clear" or "compact". */
  source?: string;
  /** Set when the session was renamed with `/rename`. */
  session_title?: string;
  /** First 500 characters of the prompt. */
  prompt?: string;
  /** UserPromptExpansion: the slash command the user typed, and its arguments. */
  command_name?: string;
  command_args?: string;
  tool_name?: string;
  tool_use_id?: string;
  /** Skill tool calls: the skill loaded. */
  skill?: string;
  /** Agent tool calls: the subagent started and its short description. */
  subagent_type?: string;
  description?: string;
  /** The file named by the `Handoff:` line of a subagent brief. */
  handoff?: string;
  /** Bash calls: first 300 characters, with secrets redacted. */
  command?: string;
  /**
   * PostToolUse and PostToolUseFailure: false when the call failed or was
   * interrupted; always false on PostToolUseFailure.
   */
  ok?: boolean;
  file_path?: string;
  /** Stop and SubagentStop: first 2000 characters of the last assistant message. */
  last_assistant_message?: string;
  /** SessionEnd: why the session ended. */
  reason?: string;
  /** SessionStart, Stop and SessionEnd: the branch checked out in `cwd`. */
  branch?: string;
  /** `gh pr create`: the first https://github.com/ URL of the response. */
  url?: string;
  /**
   * Post events of an Agent call: the `agent_id` the started subagent carries
   * on its SubagentStart, which links the call to the subagent's own events.
   */
  spawned_agent_id?: string;
};

/** The value of a handoff's `**Status:**` line. */
export type HandoffStatus = "PARTIAL" | "DONE" | "BLOCKED";

/**
 * Where a skill comes from: the org pack, the village pack, or this repo only.
 * "managed" is a synced skill whose pack the sync manifest does not record.
 */
export type SkillPack = "org" | "village" | "managed" | "local";

/** One entry of `skills.json`: one directory under `.agents/skills/`. */
export type SkillEntry = {
  name: string;
  /** Absent only on an entry with `error` set. */
  description?: string;
  when_to_use?: string;
  /** Present when the skill is scoped to matching files: one glob or a list. */
  paths?: string | string[];
  /** Path of the SKILL.md file, relative to the repository root. */
  path: string;
  /** Absent when SKILL.md could not be read. */
  lines?: number;
  managed: boolean;
  pack: SkillPack;
  /** The routing rule's section headings whose rows name the skill. */
  scopes: string[];
  /** The "Task context" cells of those rows. */
  routing: string[];
  /** Agent files whose `skills:` list names the skill. */
  preloaded_by: string[];
  /** Set when the directory has no SKILL.md or unreadable frontmatter. */
  error?: string;
};

/** The whole of `skills.json`. */
export type SkillsCatalogue = {
  generated_at: string;
  skills: SkillEntry[];
};

export type PermissionEffect = "allow" | "ask" | "deny";

/** One permission rule of a settings file. */
export type AllowRule = {
  /** The part before the parenthesis, for example "Bash". */
  tool: string;
  /** The part inside the parenthesis; null for a bare tool name. */
  pattern: string | null;
  /** First word of a Bash pattern; null for other tools. */
  program: string | null;
  effect: PermissionEffect;
  /** The settings file of the rule: "user", "project" or "local". */
  source: string;
  file: string;
  line: number;
};
