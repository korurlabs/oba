import { describe, expect, test } from "bun:test";

import { readFixture } from "@/fixtures/load";
import { parseEventRecords, parseEvents, stripHarness } from "@/lib/runs";

describe("parseEvents", () => {
  test("reports every malformed line with its number and keeps the good ones", () => {
    const parsed = parseEvents(readFixture("malformed-events.jsonl"));
    expect(parsed.events.map((event) => event.event)).toEqual(["UserPromptSubmit", "Stop"]);
    expect(parsed.problems.map((problem) => problem.line)).toEqual([2, 4, 5, 6]);
    expect(parsed.problems[0]?.reason).toStartWith("not JSON");
    expect(parsed.problems[1]?.reason).toBe("not a JSON object");
    expect(parsed.problems[2]?.reason).toBe('missing "at" or "event"');
    expect(parsed.problems[3]?.reason).toBe('unknown event "Notification"');
  });

  test("reads the recorder's own fields, including the end of a failed call", () => {
    const parsed = parseEvents(
      [
        '{"at":"2026-10-22T15:41:55Z","event":"PostToolUse","cwd":"/r","tool_name":"Agent","tool_use_id":"t1","ok":true,"spawned_agent_id":"a1"}',
        '{"at":"2026-10-22T15:41:56Z","event":"PostToolUseFailure","cwd":"/r","tool_name":"Bash","tool_use_id":"t2","command":"kazan ticket get KAI-42 --json","ok":false}',
      ].join("\n")
    );
    expect(parsed.problems).toEqual([]);
    expect(parsed.events[0]?.spawned_agent_id).toBe("a1");
    expect(parsed.events[1]?.ok).toBe(false);
  });
});

describe("harness blocks", () => {
  test("stripHarness mirrors the recorder", () => {
    expect(stripHarness("plain <system-reminder>x</system-reminder>")).toBe(
      "plain <system-reminder>x</system-reminder>"
    );
    expect(stripHarness('<agent-message from="a">hi</agent-message>\nDo it')).toBe("Do it");
    expect(
      stripHarness("<command-name>/plan</command-name><command-args>KAI-1</command-args> go")
    ).toBe("go");
    expect(stripHarness("<local-command-stdout>ok</local-command-stdout>")).toBe("");
    expect(stripHarness("<other>kept</other> text")).toBe("<other>kept</other> text");
  });

  test("parseEvents strips prompts and drops a prompt of harness blocks only", () => {
    const parsed = parseEvents(
      [
        '{"at":"2026-10-22T15:41:55Z","event":"UserPromptSubmit","cwd":"/r","prompt":"<task-notification>done</task-notification>"}',
        '{"at":"2026-10-22T15:41:56Z","event":"UserPromptSubmit","cwd":"/r","prompt":"<ide_selection>x</ide_selection> KAI-9 please"}',
      ].join("\n")
    );
    expect(parsed.events[0]?.prompt).toBeUndefined();
    expect(parsed.events[1]?.prompt).toBe("KAI-9 please");
  });
});

describe("parseEventRecords", () => {
  test("keeps the events in order and reports an entry that is not one by its position", () => {
    const parsed = parseEventRecords([
      { at: "2026-10-22T15:41:55Z", event: "SessionStart", cwd: "/r" },
      "not an event",
      { at: "2026-10-22T15:41:56Z", event: "Stop" },
    ]);
    expect(parsed.events.map((event) => event.event)).toEqual(["SessionStart", "Stop"]);
    expect(parsed.events[1]?.cwd).toBe("");
    expect(parsed.problems).toEqual([
      { line: 2, reason: "not a JSON object", text: '"not an event"' },
    ]);
  });
});
