/**
 * Reads the recorded-run fixtures for the tests, through the same parsers the
 * pages use, so a fixture that the parsers would reject fails the test.
 */

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { parseHandoff } from "@/lib/handoff";
import { handoffPathsOf } from "@/lib/session-activity";
import { parseEvents } from "@/lib/runs";
import type { HandoffStatus, RunEvent, SkillsCatalogue } from "@/types/run";

export const FIXTURES = import.meta.dir;

export type FixtureRun = {
  events: RunEvent[];
  /** Status per handoff path below `handoffs/`; null when the snapshot has no status line. */
  handoffs: ReadonlyMap<string, HandoffStatus | null>;
};

/** Loads `fixtures/runs/<name>/`: its events and the status of every handoff snapshot it holds. */
export function loadRun(name: string): FixtureRun {
  const dir = join(FIXTURES, "runs", name);
  const parsed = parseEvents(readFileSync(join(dir, "events.jsonl"), "utf8"));
  if (parsed.problems.length > 0) {
    throw new Error(`Fixture ${name} has malformed lines: ${JSON.stringify(parsed.problems)}`);
  }
  const handoffs = new Map<string, HandoffStatus | null>();
  for (const path of handoffPathsOf(parsed.events)) {
    const file = join(dir, "handoffs", ...path.split("/"));
    if (existsSync(file)) {
      handoffs.set(path, parseHandoff(readFileSync(file, "utf8")).status);
    }
  }
  return { events: parsed.events, handoffs };
}

/** Loads `fixtures/skills.json`, a skills catalogue as the recorder wrote it. */
export function loadSkills(): SkillsCatalogue {
  const catalogue = JSON.parse(readFileSync(join(FIXTURES, "skills.json"), "utf8")) as unknown;
  if (
    typeof catalogue !== "object" ||
    catalogue === null ||
    !Array.isArray((catalogue as { skills?: unknown }).skills)
  ) {
    throw new Error("fixtures/skills.json has no skills list");
  }
  return catalogue as SkillsCatalogue;
}

/** Reads any fixture file as text. */
export function readFixture(path: string): string {
  return readFileSync(join(FIXTURES, path), "utf8");
}
