/**
 * The session's timeline as a tree: what happened, in recorded order, with each
 * subagent's own work nested under it.
 *
 * A subagent started by another subagent sits under its parent, and a skill
 * sits under the agent that loaded it, so a long run reads as who did what
 * rather than as one interleaved list of parallel agents. A command a subagent
 * ran sits under that subagent in the same way.
 */

import {
  commandStarts,
  type AgentRun,
  type CommandRun,
  type PromptRun,
  type SessionActivity,
  type SkillUse,
} from "@/lib/session-activity";

/** The commands the timeline lists; other Bash calls stay off it. */
export type CommandKind = "push" | "pull-request" | "kaizen";

export type TimelineItem =
  | { kind: "prompt"; index: number; at: string; prompt: PromptRun }
  | { kind: "skill"; index: number; at: string; use: SkillUse }
  | { kind: "command"; index: number; at: string; run: CommandRun; command: CommandKind }
  | { kind: "agent"; index: number; at: string; run: AgentRun; children: TimelineItem[] };

/** The kind of a command the timeline lists, or null for one it leaves out. */
export function commandKind(command: string): CommandKind | null {
  if (commandStarts(command, "git push")) return "push";
  if (commandStarts(command, "gh pr create")) return "pull-request";
  if (commandStarts(command, "kaizen")) return "kaizen";
  return null;
}

function byIndex(left: TimelineItem, right: TimelineItem): number {
  return left.index - right.index;
}

/**
 * Builds the timeline of a session.
 *
 * @param view - The derived activity, for the prompts, agents, skills and commands.
 * @returns The top-level items in recorded order; an agent's items are its children.
 */
export function buildTimeline(view: SessionActivity): TimelineItem[] {
  const agentItems = new Map<AgentRun, TimelineItem & { kind: "agent" }>();
  for (const run of view.agents) {
    agentItems.set(run, { kind: "agent", index: run.index, at: run.startedAt, run, children: [] });
  }
  const byAgentId = (agentId: string | null): (TimelineItem & { kind: "agent" }) | null => {
    if (agentId === null) return null;
    const run = view.agents.find((candidate: AgentRun) => candidate.agentId === agentId);
    return run === undefined ? null : (agentItems.get(run) ?? null);
  };

  const top: TimelineItem[] = [];
  const place = (item: TimelineItem, parentAgentId: string | null): void => {
    const parent = byAgentId(parentAgentId);
    if (parent !== null && parent.run !== (item.kind === "agent" ? item.run : null)) {
      parent.children.push(item);
    } else {
      top.push(item);
    }
  };

  for (const prompt of view.prompts) {
    top.push({ kind: "prompt", index: prompt.index, at: prompt.at, prompt });
  }
  for (const use of view.skills) {
    place({ kind: "skill", index: use.index, at: use.at, use }, use.agentId);
  }
  for (const run of view.commands) {
    const command = commandKind(run.command);
    if (command !== null) {
      place({ kind: "command", index: run.index, at: run.at, run, command }, run.agentId);
    }
  }
  for (const item of agentItems.values()) {
    place(item, item.run.parentAgentId);
  }

  for (const item of agentItems.values()) {
    item.children.sort(byIndex);
  }
  return top.sort(byIndex);
}
