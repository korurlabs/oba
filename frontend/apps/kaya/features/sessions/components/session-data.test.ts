import { describe, expect, test } from "bun:test";
import { existsSync } from "node:fs";

import { readFixture } from "@/fixtures/load";
import { handoffPathsOf, type AgentRun } from "@/lib/session-activity";
import { parseEvents } from "@/lib/runs";

import type { SessionDetailResponse, SessionHandoffResponse } from "../schemas";
import { buildSession, duration, handoffKey, verdictOf, type SessionData } from "./session-data";

/** A session's detail as Kaya answers it. */
function detail(sessionId: string): SessionDetailResponse {
  return {
    id: `id-${sessionId}`,
    sessionId,
    title: `Session ${sessionId}`,
    ended: false,
    eventCount: 1,
    features: [],
    links: [],
    createdAt: "2026-10-20T09:00:00Z",
    subagents: [],
    skillUses: [],
    handoffs: [],
  };
}

/** The fixture's events as the API returns them: parsed JSON objects, in order. */
function records(name: string): unknown[] {
  return readFixture(`runs/${name}/events.jsonl`)
    .split("\n")
    .filter((line: string) => line.trim() !== "")
    .map((line: string) => JSON.parse(line) as unknown);
}

/** The fixture's handoff snapshots as the API returns them, paths under `handoffs/`. */
function snapshots(name: string): SessionHandoffResponse[] {
  const { events } = parseEvents(readFixture(`runs/${name}/events.jsonl`));
  return handoffPathsOf(events)
    .filter((path: string) =>
      existsSync(`${import.meta.dir}/../../../fixtures/runs/${name}/handoffs/${path}`)
    )
    .map((path: string) => ({
      path: `handoffs/${path}`,
      status: null,
      content: readFixture(`runs/${name}/handoffs/${path}`),
      capturedAt: "2026-10-20T09:00:00Z",
    }));
}

function session(name: string): SessionData {
  return buildSession(detail(name), records(name), snapshots(name));
}

describe("buildSession", () => {
  test("reads the handoff markdown and derives the activity from the events", () => {
    const data = session("implement-running");
    expect(data.problems).toEqual([]);
    expect(data.handoffs.get("board-export/backend/api.md")?.parsed.status).toBe("DONE");
    expect(data.handoffs.get("board-export/backend/api.md")?.markdown).toContain("**Status:**");
    expect(data.view.agents.some((run) => run.active)).toBe(true);
  });

  test("leaves out a recorded entry that is not an event and reports it", () => {
    const data = buildSession(detail("plain"), [...records("plain"), { at: "x" }, 7], []);
    expect(data.problems.map((problem) => problem.line)).toEqual([
      records("plain").length + 1,
      records("plain").length + 2,
    ]);
    expect(data.events).toHaveLength(records("plain").length);
  });
});

describe("handoffKey", () => {
  test("strips a leading handoffs/ only", () => {
    expect(handoffKey("handoffs/a/b.md")).toBe("a/b.md");
    expect(handoffKey("a/handoffs/b.md")).toBe("a/handoffs/b.md");
  });
});

describe("verdictOf", () => {
  const base: AgentRun = {
    toolUseId: null,
    agentId: "a",
    parentAgentId: null,
    subagentType: "coding-engineer",
    description: null,
    handoff: null,
    startedAt: "2026-10-20T09:00:00Z",
    index: 0,
    stoppedAt: "2026-10-20T09:01:00Z",
    active: false,
    lastMessage: null,
    skills: [],
  };

  test("a reviewer reports its worst verdict, an implementer its status word", async () => {
    const handoffs = new Map();
    expect(
      verdictOf(
        {
          ...base,
          subagentType: "feature-code-reviewer",
          lastMessage: "Not APPROVED: CRITICAL_ISSUES",
        },
        handoffs
      )
    ).toBe("CRITICAL_ISSUES");
    expect(verdictOf({ ...base, lastMessage: "PARTIAL: tests remain" }, handoffs)).toBe("PARTIAL");
    expect(verdictOf({ ...base, active: true, lastMessage: "DONE" }, handoffs)).toBeNull();
  });

  test("falls back to the handoff's status", () => {
    const data = session("implement-running");
    const run = data.view.agents.find((agent) => agent.agentId === "a-api") as AgentRun;
    expect(verdictOf({ ...run, lastMessage: "Finished." }, data.handoffs)).toBe("DONE");
  });
});

describe("duration", () => {
  test("formats seconds, minutes and hours", () => {
    expect(duration("2026-10-20T09:00:00Z", "2026-10-20T09:00:45Z")).toBe("45 s");
    expect(duration("2026-10-20T09:00:00Z", "2026-10-20T09:03:05Z")).toBe("3 min 05 s");
    expect(duration("2026-10-20T09:00:00Z", "2026-10-20T10:02:00Z")).toBe("1 h 02 min");
    expect(duration("2026-10-20T09:00:00Z", null)).toBeNull();
  });
});
