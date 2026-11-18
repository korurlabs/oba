/**
 * The parsers that read recorded hook events, from Kaya's API or from a
 * recorder `events.jsonl` (the test fixtures).
 *
 * A malformed event does not fail the whole session: it is left out of the
 * events and returned as an `EventProblem`, so the page can show it.
 */

import type { HookEventName, RunEvent } from "@/types/run";

/** One line of `events.jsonl` that could not be read as a RunEvent. */
export type EventProblem = {
  /** 1-based line number in the file. */
  line: number;
  reason: string;
  /** The start of the line, for display. */
  text: string;
};

/** The events of one session, in recorded order, and the lines that were left out. */
export type ParsedEvents = {
  events: RunEvent[];
  problems: EventProblem[];
};

const PROBLEM_TEXT_LIMIT = 120;
/**
 * One block Claude Code puts in front of what the user typed: the file or
 * selection open in the IDE, reminders, messages and notifications from
 * subagents, and slash command echoes. A block that is never closed runs to
 * the end, as when the prompt was cut inside it. The recorder strips the same
 * blocks; events it wrote before it did still carry them.
 */
const HARNESS_BLOCK =
  /^\s*<(ide_[a-z_]+|system-reminder|agent-message|task-notification|command-[a-z-]+|local-command-[a-z-]+)(?:\s[^>]*)?>[\s\S]*?(?:<\/\1>|$)/;

const HOOK_EVENTS: ReadonlySet<string> = new Set<HookEventName>([
  "SessionStart",
  "UserPromptSubmit",
  "UserPromptExpansion",
  "PreToolUse",
  "PostToolUse",
  "PostToolUseFailure",
  "SubagentStart",
  "SubagentStop",
  "Stop",
  "SessionEnd",
]);

/**
 * `text` without the harness blocks in front of the user's own text, "" when
 * it holds nothing else. Text that starts with the user's own words is
 * returned unchanged.
 */
export function stripHarness(text: string): string {
  let stripped = text;
  let found = HARNESS_BLOCK.exec(stripped);
  while (found) {
    stripped = stripped.slice(found[0].length);
    found = HARNESS_BLOCK.exec(stripped);
  }
  return stripped === text ? text : stripped.trim();
}

/** `text` stripped of harness blocks, or null when nothing else remains. */
function userText(text: unknown): string | null {
  if (typeof text !== "string") {
    return null;
  }
  const stripped = stripHarness(text);
  return stripped === "" ? null : stripped;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/**
 * Reads one recorded event, already parsed from JSON, as a RunEvent.
 *
 * @returns The event, or the reason it cannot be one: not an object, no `at`
 *   or `event`, or an event the recorder is not registered for. A prompt loses
 *   its leading harness blocks, and the key is dropped when nothing else
 *   remains, so older events read like the ones the recorder writes now.
 */
function readEvent(value: unknown): RunEvent | string {
  if (!isRecord(value)) {
    return "not a JSON object";
  }
  if (typeof value.at !== "string" || typeof value.event !== "string") {
    return 'missing "at" or "event"';
  }
  if (!HOOK_EVENTS.has(value.event)) {
    return `unknown event "${value.event}"`;
  }
  const event = { ...value, cwd: typeof value.cwd === "string" ? value.cwd : "" } as RunEvent;
  if (event.prompt !== undefined) {
    const prompt = userText(event.prompt);
    if (prompt === null) {
      delete event.prompt;
    } else {
      event.prompt = prompt;
    }
  }
  return event;
}

/**
 * Reads `events.jsonl`: one RunEvent per non-empty line.
 *
 * A line that is not JSON or not an event (see `readEvent`) is left out and
 * reported in `problems` with its line number.
 */
export function parseEvents(text: string): ParsedEvents {
  const events: RunEvent[] = [];
  const problems: EventProblem[] = [];
  text.split("\n").forEach((raw: string, index: number) => {
    const line = raw.trim();
    if (line === "") {
      return;
    }
    const report = (reason: string): void => {
      problems.push({ line: index + 1, reason, text: line.slice(0, PROBLEM_TEXT_LIMIT) });
    };
    let value: unknown;
    try {
      value = JSON.parse(line) as unknown;
    } catch (error) {
      report(`not JSON: ${errorMessage(error)}`);
      return;
    }
    const event = readEvent(value);
    if (typeof event === "string") {
      report(event);
      return;
    }
    events.push(event);
  });
  return { events, problems };
}

/**
 * Reads the events Kaya's API returns for a session, in the order given (by
 * `seq`). An entry that is not an event is left out and reported in
 * `problems`, numbered by its position from 1.
 */
export function parseEventRecords(records: readonly unknown[]): ParsedEvents {
  const events: RunEvent[] = [];
  const problems: EventProblem[] = [];
  records.forEach((value: unknown, index: number) => {
    const event = readEvent(value);
    if (typeof event === "string") {
      problems.push({
        line: index + 1,
        reason: event,
        text: JSON.stringify(value ?? null).slice(0, PROBLEM_TEXT_LIMIT),
      });
      return;
    }
    events.push(event);
  });
  return { events, problems };
}
