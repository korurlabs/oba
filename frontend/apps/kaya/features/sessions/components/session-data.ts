/**
 * What the session page shows, built from Kaya's answers for one session: the
 * detail, the recorded events and the handoff snapshots. The page needs the
 * handoffs' markdown, not only their status, because it renders them.
 */

import { parseHandoff, type ParsedHandoff } from "@/lib/handoff";
import { deriveActivity, type AgentRun, type SessionActivity } from "@/lib/session-activity";
import { parseEventRecords, type EventProblem } from "@/lib/runs";
import type { RunEvent } from "@/types/run";

import type { SessionDetailResponse, SessionHandoffResponse } from "../schemas";

/** One handoff snapshot as the sheet shows it. */
export type LoadedHandoff = {
  markdown: string;
  parsed: ParsedHandoff;
};

export type SessionData = {
  detail: SessionDetailResponse;
  events: RunEvent[];
  /** Recorded events that were left out as unreadable. */
  problems: EventProblem[];
  view: SessionActivity;
  /** Every snapshot, by its path below `handoffs/`. */
  handoffs: ReadonlyMap<string, LoadedHandoff>;
};

const HANDOFFS_PREFIX = "handoffs/";

/** A snapshot's path below `handoffs/`, the form the events use. */
export function handoffKey(path: string): string {
  return path.startsWith(HANDOFFS_PREFIX) ? path.slice(HANDOFFS_PREFIX.length) : path;
}

/** The snapshots by path, parsed for their status and sections. */
export function loadHandoffs(
  snapshots: readonly SessionHandoffResponse[]
): Map<string, LoadedHandoff> {
  return new Map(
    snapshots.map((snapshot: SessionHandoffResponse) => [
      handoffKey(snapshot.path),
      { markdown: snapshot.content, parsed: parseHandoff(snapshot.content) },
    ])
  );
}

/**
 * The session page's data. Its activity is derived here, in the browser, from
 * the events, so the timeline needs no stored copy of them.
 */
export function buildSession(
  detail: SessionDetailResponse,
  records: readonly unknown[],
  snapshots: readonly SessionHandoffResponse[]
): SessionData {
  const { events, problems } = parseEventRecords(records);
  return {
    detail,
    events,
    problems,
    view: deriveActivity(events),
    handoffs: loadHandoffs(snapshots),
  };
}

const REVIEWER = "feature-code-reviewer";
/** A reviewer's verdicts, worst first, so a message that quotes several reports the worst. */
const REVIEW_VERDICTS = ["CRITICAL_ISSUES", "NEEDS_CHANGES", "APPROVED"];
/** The status words an implementing role starts its report with. */
const VERDICTS = [...REVIEW_VERDICTS, "BLOCKED", "PARTIAL", "DONE"];

/**
 * What a stopped subagent reported: a reviewer's verdict or a status word at
 * the start of its last message, else its handoff's status; null while it
 * runs or when it reported neither.
 */
export function verdictOf(
  run: AgentRun,
  handoffs: ReadonlyMap<string, LoadedHandoff>
): string | null {
  if (run.active) {
    return null;
  }
  const message = run.lastMessage ?? "";
  const reviewerVerdict =
    run.subagentType === REVIEWER
      ? REVIEW_VERDICTS.find((word: string) => message.includes(word))
      : undefined;
  if (reviewerVerdict !== undefined) {
    return reviewerVerdict;
  }
  const leading = /^\W*([A-Z_]+)\b/.exec(message)?.[1];
  if (leading !== undefined && VERDICTS.includes(leading)) {
    return leading;
  }
  return run.handoff === null ? null : (handoffs.get(run.handoff)?.parsed.status ?? null);
}

/** "45 s", "3 min 05 s", "1 h 02 min" between two ISO times; null when either is unknown. */
export function duration(from: string, to: string | null): string | null {
  if (to === null) {
    return null;
  }
  const start = Date.parse(from);
  const end = Date.parse(to);
  if (Number.isNaN(start) || Number.isNaN(end)) {
    return null;
  }
  const seconds = Math.max(0, Math.round((end - start) / 1000));
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ${String(seconds % 60).padStart(2, "0")} s`;
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, "0")} min`;
}

/** The local time of day of an ISO time, for the timeline and the sheet. */
export function clockTime(iso: string): string {
  const time = Date.parse(iso);
  if (Number.isNaN(time)) return iso;
  return new Date(time).toLocaleTimeString("en-GB", { hour12: false });
}
