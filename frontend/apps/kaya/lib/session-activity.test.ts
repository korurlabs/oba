import { describe, expect, test } from "bun:test";

import { loadRun, loadSkills } from "@/fixtures/load";
import { commandStarts, deriveActivity, type SessionActivity } from "@/lib/session-activity";
import type { RunEvent } from "@/types/run";

function derive(name: string): SessionActivity {
  return deriveActivity(loadRun(name).events, loadSkills());
}

describe("deriveActivity", () => {
  test("a plain session has its one command and no subagent", () => {
    const view = derive("plain");
    expect(view.commands).toHaveLength(1);
    expect(view.agents).toHaveLength(0);
  });

  test("a session that stopped for an answer is idle with its last message", () => {
    const view = derive("plan-waiting");
    expect(view.idle).toBe(true);
    expect(view.lastAssistantMessage).toContain("Do you approve it?");
    expect(view.agents.map((run) => run.subagentType)).toEqual(["requirements-analyst"]);
  });

  test("a running subagent stays active while the stopped ones are not", () => {
    const view = derive("implement-running");
    expect(view.agents.filter((run) => run.active)).toHaveLength(1);
    expect(view.agents.length).toBeGreaterThan(1);
  });

  test("a successful gh pr create keeps the pull request URL on its command", () => {
    const view = derive("pull-request");
    const created = view.commands.find((run) => commandStarts(run.command, "gh pr create"));
    expect(created?.ok).toBe(true);
    expect(created?.url).toBe("https://github.com/senseylabs/village/pull/643");
  });

  test("a subagent's commands carry its agent id and their result", () => {
    const events: RunEvent[] = [
      {
        at: "2026-10-20T09:00:00Z",
        event: "PreToolUse",
        cwd: "/r",
        tool_name: "Agent",
        tool_use_id: "t1",
        subagent_type: "coding-engineer",
        handoff: "kai-1/backend/api.md",
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
        command: "git push -u origin kai-1",
      },
      {
        at: "2026-10-20T09:00:03Z",
        event: "PostToolUseFailure",
        cwd: "/r",
        agent_id: "a1",
        tool_name: "Bash",
        tool_use_id: "t2",
        command: "git push -u origin kai-1",
        ok: false,
      },
      {
        at: "2026-10-20T09:00:04Z",
        event: "PreToolUse",
        cwd: "/r",
        tool_name: "Bash",
        tool_use_id: "t3",
        command: "kazan comment add KAI-1 --json",
      },
    ];
    const view = deriveActivity(events);
    expect(view.commands.map((run) => [run.command, run.agentId, run.ok])).toEqual([
      ["git push -u origin kai-1", "a1", false],
      ["kazan comment add KAI-1 --json", null, null],
    ]);
    expect(view.agents[0]?.handoff).toBe("kai-1/backend/api.md");
    expect(view.agents[0]?.agentId).toBe("a1");
  });

  test("the prompts come in recorded order", () => {
    expect(derive("deliver-full").prompts.map((prompt) => prompt.text)).toEqual([
      "/deliver KAI-42 add the CSV export",
      "Approved.",
    ]);
  });
});

describe("skills", () => {
  test("a Skill call of a feature-code-reviewer sits next to its preloaded skill", () => {
    const view = derive("review-critical");
    const reviewer = view.agents.find((run) => run.subagentType === "feature-code-reviewer");
    expect(reviewer?.skills.map((use) => [use.name, use.source])).toEqual([
      ["code-review-convention", "preloaded"],
      ["no-silent-failures", "tool"],
    ]);
  });

  test("a Skill call of the session belongs to no subagent", () => {
    const view = derive("plan-waiting");
    expect(view.skills.filter((use) => use.agentId === null).map((use) => use.name)).toContain(
      "agent-config-location"
    );
  });

  test("a skill of a subagent without SubagentStop is in use, and one of a stopped subagent is not", () => {
    const view = derive("implement-running");
    const testing = view.skills.find((use) => use.name === "testing-patterns");
    const schema = view.skills.find((use) => use.name === "schema-convention");
    expect(testing?.inUse).toBe(true);
    expect(schema?.inUse).toBe(false);
  });

  test("every skill of a session that ended is no longer in use", () => {
    const view = derive("session-end");
    expect(view.ended).toBe(true);
    expect(view.skills.length).toBeGreaterThan(0);
    expect(view.skills.every((use) => !use.inUse)).toBe(true);
  });
});

describe("commandStarts", () => {
  test("matches any part of a command line as whole words", () => {
    expect(commandStarts("cd /repo && git push -u origin x", "git push")).toBe(true);
    expect(commandStarts("git pushx", "git push")).toBe(false);
    expect(commandStarts("echo git push", "git push")).toBe(false);
  });
});
