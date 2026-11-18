/**
 * What one session did, read from its recorded events: its subagents with the
 * skills each loaded, the skills the session loaded itself, its Bash commands
 * and the user's prompts, and whether its turn ended and waits for the user.
 *
 * The recorder keeps hook events only, so this module replays them in order.
 */

import type { RunEvent, SkillsCatalogue } from "@/types/run";

/** How a skill came to be used: a Skill call, a slash command the user typed, or the agent file's preload list. */
export type SkillSource = "tool" | "command" | "preloaded";

export type SkillUse = {
  name: string;
  source: SkillSource;
  at: string;
  /** Position of the event in the session, for ordering. */
  index: number;
  /** The subagent that loaded it; null for the session. */
  agentId: string | null;
  /** True while the agent that loaded it has not stopped. */
  inUse: boolean;
};

/** One subagent of the session, from its Agent call and its start and stop events. */
export type AgentRun = {
  /** The Agent call that started it; null when only its SubagentStart was recorded. */
  toolUseId: string | null;
  /** Null until its SubagentStart is matched. */
  agentId: string | null;
  /** The subagent that made the Agent call; null for the session. */
  parentAgentId: string | null;
  subagentType: string;
  description: string | null;
  /** The handoff the brief names, as the path below `handoffs/`. */
  handoff: string | null;
  startedAt: string;
  index: number;
  stoppedAt: string | null;
  active: boolean;
  lastMessage: string | null;
  /** Preloaded skills first, then the loaded ones in load order. */
  skills: SkillUse[];
};

/** One Bash command the session or one of its subagents ran. */
export type CommandRun = {
  toolUseId: string | null;
  /** The subagent that ran it; null for the session. */
  agentId: string | null;
  command: string;
  at: string;
  index: number;
  /** Null until the call has ended. */
  ok: boolean | null;
  /** The pull request URL of a `gh pr create`. */
  url: string | null;
};

/** One prompt the user sent. */
export type PromptRun = {
  text: string;
  at: string;
  index: number;
};

export type SessionActivity = {
  ended: boolean;
  /** True when the session's last turn ended and the user has not answered. */
  idle: boolean;
  lastAssistantMessage: string | null;
  agents: AgentRun[];
  /** Every skill use of the session, in load order. */
  skills: SkillUse[];
  /** Every Bash command of the session and its subagents, in recorded order. */
  commands: CommandRun[];
  /** The user's prompts, in recorded order. */
  prompts: PromptRun[];
};

const AGENT_TOOLS: ReadonlySet<string> = new Set(["Agent", "Task"]);
const WRITE_TOOLS: ReadonlySet<string> = new Set(["Edit", "Write", "MultiEdit"]);
const HANDOFFS_SEGMENT = /(?:^|\/)handoffs\/(.+)$/;
const COMMAND_SEPARATORS = /&&|\|\||[;|\n(]/;

function isPost(event: RunEvent): boolean {
  return event.event === "PostToolUse" || event.event === "PostToolUseFailure";
}

function okOf(event: RunEvent): boolean {
  return event.event === "PostToolUseFailure" ? false : event.ok !== false;
}

/** The path below `handoffs/` of a written file, or null when it is not a handoff. */
export function handoffPathOf(filePath: string): string | null {
  const found = HANDOFFS_SEGMENT.exec(filePath.replace(/\\/g, "/"));
  return found?.[1] ?? null;
}

/**
 * Every handoff a session names or writes, as paths below `handoffs/`, in first
 * mention order.
 */
export function handoffPathsOf(events: readonly RunEvent[]): string[] {
  const paths: string[] = [];
  const add = (path: string | null): void => {
    if (path !== null && !paths.includes(path)) {
      paths.push(path);
    }
  };
  for (const event of events) {
    if (event.handoff) {
      add(event.handoff);
    }
    if (event.file_path && event.tool_name && WRITE_TOOLS.has(event.tool_name)) {
      add(handoffPathOf(event.file_path));
    }
  }
  return paths;
}

/**
 * True when one part of a shell command line starts with `prefix` as whole
 * words, the way the server matches a workflow's COMMAND check.
 */
export function commandStarts(command: string, prefix: string): boolean {
  return command
    .split(COMMAND_SEPARATORS)
    .map((part: string) => part.trim())
    .some((part: string) => part === prefix || part.startsWith(`${prefix} `));
}

/**
 * Derives the subagents, skills, commands and prompts of a session from its
 * events in recorded order.
 *
 * @param events - The session's events as `parseEvents` returns them.
 * @param catalogue - The skills catalogue, for preloaded skills and to tell
 *   skills from other slash commands; without it no skill counts as preloaded
 *   and every slash command counts as a skill.
 */
export function deriveActivity(
  events: readonly RunEvent[],
  catalogue: SkillsCatalogue | null = null
): SessionActivity {
  const catalogueNames =
    catalogue === null ? null : new Set(catalogue.skills.map((skill) => skill.name));
  const preloadsOf = (subagentType: string): string[] =>
    catalogue === null
      ? []
      : catalogue.skills
          .filter((skill) => skill.preloaded_by.includes(subagentType))
          .map((skill) => skill.name);

  // The Post event of an Agent call names the subagent it started; reading
  // these first lets a SubagentStart find its call even when the Post comes
  // later, as it does for a subagent that runs in the foreground.
  const spawned = new Map<string, string>();
  for (const event of events) {
    if (
      isPost(event) &&
      event.tool_name &&
      AGENT_TOOLS.has(event.tool_name) &&
      event.spawned_agent_id &&
      event.tool_use_id
    ) {
      spawned.set(event.spawned_agent_id, event.tool_use_id);
    }
  }
  const spawningCalls = new Set(spawned.values());

  const agents: AgentRun[] = [];
  const commands: CommandRun[] = [];
  const prompts: PromptRun[] = [];
  const skills: SkillUse[] = [];
  let ended = false;
  let idle = false;
  let lastAssistantMessage: string | null = null;

  const agentById = (agentId: string): AgentRun | undefined =>
    agents.find((run: AgentRun) => run.agentId === agentId);

  const addPreloads = (run: AgentRun, at: string, index: number): void => {
    for (const name of preloadsOf(run.subagentType)) {
      const use: SkillUse = {
        name,
        source: "preloaded",
        at,
        index,
        agentId: run.agentId,
        inUse: true,
      };
      run.skills.push(use);
      skills.push(use);
    }
  };

  const newAgent = (
    event: RunEvent,
    index: number,
    subagentType: string,
    toolUseId: string | null
  ): AgentRun => {
    const run: AgentRun = {
      toolUseId,
      agentId: null,
      parentAgentId: toolUseId === null ? null : (event.agent_id ?? null),
      subagentType,
      description: event.description ?? null,
      handoff: event.handoff ?? null,
      startedAt: event.at,
      index,
      stoppedAt: null,
      active: true,
      lastMessage: null,
      skills: [],
    };
    agents.push(run);
    addPreloads(run, event.at, index);
    return run;
  };

  const useSkill = (name: string, source: SkillSource, event: RunEvent, index: number): void => {
    if (event.agent_id) {
      const run = agentById(event.agent_id);
      const use: SkillUse = {
        name,
        source,
        at: event.at,
        index,
        agentId: event.agent_id,
        inUse: true,
      };
      run?.skills.push(use);
      skills.push(use);
      return;
    }
    if (catalogueNames !== null && !catalogueNames.has(name)) {
      return;
    }
    skills.push({ name, source, at: event.at, index, agentId: null, inUse: true });
  };

  events.forEach((event: RunEvent, index: number) => {
    const inSession =
      !event.agent_id || event.event === "SubagentStart" || event.event === "SubagentStop";
    switch (event.event) {
      case "UserPromptSubmit": {
        idle = false;
        if (event.prompt) {
          prompts.push({ text: event.prompt, at: event.at, index });
        }
        return;
      }
      case "UserPromptExpansion": {
        idle = false;
        if (event.command_name) {
          useSkill(event.command_name, "command", event, index);
        }
        return;
      }
      case "Stop": {
        if (!event.agent_id) {
          idle = true;
          lastAssistantMessage = event.last_assistant_message ?? null;
        }
        return;
      }
      case "SessionEnd": {
        ended = true;
        return;
      }
      case "SubagentStart": {
        const agentId = event.agent_id;
        if (!agentId) {
          return;
        }
        const callId = spawned.get(agentId);
        const run =
          agents.find(
            (candidate) =>
              candidate.agentId === null && callId !== undefined && candidate.toolUseId === callId
          ) ??
          agents.find(
            (candidate) =>
              candidate.agentId === null &&
              candidate.active &&
              candidate.subagentType === event.agent_type &&
              (candidate.toolUseId === null || !spawningCalls.has(candidate.toolUseId))
          ) ??
          newAgent(event, index, event.agent_type ?? "unknown", null);
        run.agentId = agentId;
        for (const use of run.skills) {
          use.agentId = agentId;
        }
        return;
      }
      case "SubagentStop": {
        const run = event.agent_id ? agentById(event.agent_id) : undefined;
        if (run === undefined) {
          return;
        }
        run.active = false;
        run.stoppedAt = event.at;
        run.lastMessage = event.last_assistant_message ?? null;
        return;
      }
      default:
        break;
    }

    if (event.event === "PreToolUse" && inSession) {
      idle = false;
    }
    const tool = event.tool_name ?? "";
    if (AGENT_TOOLS.has(tool)) {
      if (event.event === "PreToolUse") {
        newAgent(event, index, event.subagent_type ?? "unknown", event.tool_use_id ?? null);
      } else if (isPost(event)) {
        const run = agents.find(
          (candidate) => candidate.toolUseId !== null && candidate.toolUseId === event.tool_use_id
        );
        if (run !== undefined && run.agentId === null) {
          if (event.spawned_agent_id) {
            run.agentId = event.spawned_agent_id;
          } else if (!okOf(event)) {
            // The call failed before a subagent existed.
            run.active = false;
            run.stoppedAt = event.at;
          }
        }
      }
      return;
    }
    if (tool === "Skill") {
      if (event.event === "PreToolUse" && event.skill) {
        useSkill(event.skill, "tool", event, index);
      }
      return;
    }
    if (tool === "Bash" && event.command) {
      if (event.event === "PreToolUse") {
        commands.push({
          toolUseId: event.tool_use_id ?? null,
          agentId: inSession ? null : (event.agent_id ?? null),
          command: event.command,
          at: event.at,
          index,
          ok: null,
          url: null,
        });
      } else if (isPost(event)) {
        const run = commands.find(
          (candidate) => candidate.toolUseId !== null && candidate.toolUseId === event.tool_use_id
        );
        if (run !== undefined) {
          run.ok = okOf(event);
          run.url = event.url ?? null;
        }
      }
    }
  });

  if (ended) {
    for (const run of agents) {
      run.active = false;
    }
  }
  for (const use of skills) {
    const run = use.agentId === null ? undefined : agentById(use.agentId);
    use.inUse = use.agentId === null ? !ended : run !== undefined && run.active;
  }
  for (const run of agents) {
    for (const use of run.skills) {
      use.inUse = run.active;
    }
  }

  return { ended, idle, lastAssistantMessage, agents, skills, commands, prompts };
}
