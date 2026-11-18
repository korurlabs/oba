import { describe, expect, test } from "bun:test";

import { loadRun, loadSkills } from "@/fixtures/load";
import { deriveActivity } from "@/lib/session-activity";
import type { RunEvent } from "@/types/run";

import { buildTimeline, commandKind, type TimelineItem } from "./timeline-data";

function timelineOf(name: string): TimelineItem[] {
  return buildTimeline(deriveActivity(loadRun(name).events, loadSkills()));
}

function flatten(items: readonly TimelineItem[]): TimelineItem[] {
  return items.flatMap((item) =>
    item.kind === "agent" ? [item, ...flatten(item.children)] : [item]
  );
}

describe("buildTimeline", () => {
  test("skills sit under the agent that loaded them, prompts at the top", () => {
    const items = timelineOf("implement-running");
    expect(items[0]?.kind).toBe("prompt");
    const api = items.find((item) => item.kind === "agent" && item.run.agentId === "a-api");
    if (api?.kind !== "agent") throw new Error("Expected the a-api agent");
    const names = api.children.flatMap((item) => (item.kind === "skill" ? [item.use.name] : []));
    expect(names).toContain("entity-dto-convention");
    const topSkills = items.flatMap((item) => (item.kind === "skill" ? [item.use.name] : []));
    expect(topSkills).not.toContain("entity-dto-convention");
  });

  test("items are in recorded order at every level", () => {
    const check = (items: readonly TimelineItem[]): void => {
      const order = items.map((item) => item.index);
      expect(order).toEqual([...order].sort((left, right) => left - right));
      for (const item of items) if (item.kind === "agent") check(item.children);
    };
    check(timelineOf("deliver-full"));
  });

  test("a full run lists its push, pull request and Kaizen calls", () => {
    const kinds = flatten(timelineOf("deliver-full")).flatMap((item) =>
      item.kind === "command" ? [item.command] : []
    );
    expect(kinds).toContain("push");
    expect(kinds).toContain("pull-request");
    expect(kinds).toContain("kaizen");
  });

  test("a subagent's push sits under the subagent", () => {
    const events: RunEvent[] = [
      {
        at: "2026-10-20T09:00:00Z",
        event: "PreToolUse",
        cwd: "/r",
        tool_name: "Agent",
        tool_use_id: "t1",
        subagent_type: "coding-engineer",
      },
      {
        at: "2026-10-20T09:00:01Z",
        event: "SubagentStart",
        cwd: "/r",
        agent_id: "a1",
        agent_type: "coding-engineer",
      },
      {
        at: "2026-10-20T09:00:02Z",
        event: "PreToolUse",
        cwd: "/r",
        agent_id: "a1",
        tool_name: "Bash",
        tool_use_id: "t2",
        command: "git push -u origin b",
      },
      {
        at: "2026-10-20T09:00:03Z",
        event: "PostToolUse",
        cwd: "/r",
        agent_id: "a1",
        tool_name: "Bash",
        tool_use_id: "t2",
        command: "git push -u origin b",
        ok: true,
      },
    ];
    const items = buildTimeline(deriveActivity(events));
    expect(items).toHaveLength(1);
    const agent = items[0];
    if (agent?.kind !== "agent") throw new Error("Expected an agent");
    const push = agent.children[0];
    if (push?.kind !== "command") throw new Error("Expected the push under the agent");
    expect(push.command).toBe("push");
    expect(push.run.ok).toBe(true);
  });

  test("a subagent started by a subagent sits under its parent", () => {
    const events: RunEvent[] = [
      {
        at: "2026-10-20T09:00:00Z",
        event: "PreToolUse",
        cwd: "/r",
        tool_name: "Agent",
        tool_use_id: "t1",
        subagent_type: "coding-engineer",
      },
      {
        at: "2026-10-20T09:00:01Z",
        event: "SubagentStart",
        cwd: "/r",
        agent_id: "outer",
        agent_type: "coding-engineer",
      },
      {
        at: "2026-10-20T09:00:02Z",
        event: "PreToolUse",
        cwd: "/r",
        agent_id: "outer",
        tool_name: "Agent",
        tool_use_id: "t2",
        subagent_type: "Explore",
      },
      {
        at: "2026-10-20T09:00:03Z",
        event: "SubagentStart",
        cwd: "/r",
        agent_id: "inner",
        agent_type: "Explore",
      },
    ];
    const items = buildTimeline(deriveActivity(events));
    expect(items).toHaveLength(1);
    const outer = items[0];
    if (outer?.kind !== "agent") throw new Error("Expected an agent");
    expect(
      outer.children.map((item) => (item.kind === "agent" ? item.run.subagentType : item.kind))
    ).toEqual(["Explore"]);
  });
});

describe("commandKind", () => {
  test("lists pushes, pull requests and Kaizen calls only", () => {
    expect(commandKind("cd x && git push -u origin b")).toBe("push");
    expect(commandKind("gh pr create --base main")).toBe("pull-request");
    expect(commandKind("kaizen ticket get KAI-1 --json")).toBe("kaizen");
    expect(commandKind("bun run test")).toBeNull();
  });
});
