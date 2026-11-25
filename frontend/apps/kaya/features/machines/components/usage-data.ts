import { ageText } from "@/lib/format-time";

import type { MachineUsageResponse, MachineUsageWindow } from "../schemas";
import { NOT_REPORTED } from "./machine-text";

/** A usage window's names: in the usage panel, and in the compact cell of the machines table. */
export type UsageWindowName = { label: string; shortLabel: string };

export const FIVE_HOUR: UsageWindowName = { label: "5-hour", shortLabel: "5h" };
export const WEEKLY: UsageWindowName = { label: "Weekly", shortLabel: "Week" };

/** What one bar of the usage cell shows. */
export type UsageWindowView =
  | {
      kind: "used";
      label: string;
      shortLabel: string;
      /** 0 to 100, rounded for the text; the bar takes it as is. */
      percentage: number;
      /** "23%" */
      percentageText: string;
      /** "resets 14:30, in 2 h 05 min" */
      resetText: string;
    }
  | {
      /** The window's reset time has passed: no percentage, an empty bar. */
      kind: "reset";
      label: string;
      shortLabel: string;
      /** "Reset at 14:30" */
      resetText: string;
    }
  | { kind: "missing"; label: string; shortLabel: string };

/** What the whole cell shows when the account has a snapshot. */
export type KnownUsageView = {
  kind: "known";
  windows: UsageWindowView[];
  /** "updated 4 min ago" */
  ageText: string | null;
  /** "Shared with ada-mini, ada-studio · reported by ada-mini", or null for an account of one machine. */
  sharedText: string | null;
};

/** What the whole cell shows. */
export type UsageView = { kind: "unknown"; text: string } | KnownUsageView;

export const NO_ACCOUNT_TEXT = "Unknown: no Claude subscription reported";
export const NO_SNAPSHOT_TEXT = "Unknown: no usage reported yet";

/** The compact cell's text beside a window whose reset time has passed. */
export const RESET_VALUE_TEXT = "reset";

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/** "in 12 min", "in 2 h 05 min" or "in 3 d 4 h"; "in under a minute" just before the reset. */
export function countdownText(resetsAt: number, now: number): string {
  const left = resetsAt - now;
  if (left < MINUTE_MS) return "in under a minute";
  if (left < HOUR_MS) return `in ${Math.floor(left / MINUTE_MS)} min`;
  if (left < DAY_MS) {
    const hours = Math.floor(left / HOUR_MS);
    const minutes = Math.floor((left % HOUR_MS) / MINUTE_MS);
    return `in ${hours} h ${String(minutes).padStart(2, "0")} min`;
  }
  const days = Math.floor(left / DAY_MS);
  const hours = Math.floor((left % DAY_MS) / HOUR_MS);
  return `in ${days} d ${hours} h`;
}

/**
 * The reset time in the viewer's time zone: "14:30" today, "Mon 14:30" within
 * a week, else the date as well. `timeZone` is for tests; the browser's own
 * zone applies when it is left out.
 */
export function resetClockText(resetsAt: number, now: number, timeZone?: string): string {
  const date = new Date(resetsAt);
  const day = (value: Date) =>
    new Intl.DateTimeFormat("en-CA", {
      timeZone,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(value);
  const time = new Intl.DateTimeFormat(undefined, {
    timeZone,
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
  if (day(date) === day(new Date(now))) return time;
  if (Math.abs(resetsAt - now) < 6 * DAY_MS) {
    const weekday = new Intl.DateTimeFormat(undefined, { timeZone, weekday: "short" }).format(date);
    return `${weekday} ${time}`;
  }
  const dateText = new Intl.DateTimeFormat(undefined, {
    timeZone,
    day: "numeric",
    month: "short",
  }).format(date);
  return `${dateText} ${time}`;
}

/**
 * One window as a bar. The server marks a window `reset` when it answered,
 * but the page stays open, so the reset is also checked against `now`: once
 * `resetsAt` passes, the old percentage is never shown.
 */
export function windowView(
  name: UsageWindowName,
  window: MachineUsageWindow | null | undefined,
  now: number,
  timeZone?: string
): UsageWindowView {
  if (window === null || window === undefined) return { kind: "missing", ...name };
  const resetsAt = Date.parse(window.resetsAt);
  if (Number.isNaN(resetsAt)) return { kind: "missing", ...name };
  const clock = resetClockText(resetsAt, now, timeZone);
  const used =
    window.usedPercentage === null || window.usedPercentage === undefined
      ? Number.NaN
      : Number(window.usedPercentage);
  if (window.reset || resetsAt <= now || Number.isNaN(used)) {
    return { kind: "reset", ...name, resetText: `Reset at ${clock}` };
  }
  const percentage = Math.min(100, Math.max(0, used));
  return {
    kind: "used",
    ...name,
    percentage,
    percentageText: `${Math.round(percentage)}%`,
    resetText: `resets ${clock}, ${countdownText(resetsAt, now)}`,
  };
}

/** The usage cell of one machine, from its `usage` answer and the current time. */
export function usageView(
  usage: MachineUsageResponse | null | undefined,
  now: number,
  timeZone?: string
): UsageView {
  if (usage === null || usage === undefined || usage.state === "NO_ACCOUNT") {
    return { kind: "unknown", text: NO_ACCOUNT_TEXT };
  }
  if (usage.state === "NO_SNAPSHOT") {
    return { kind: "unknown", text: NO_SNAPSHOT_TEXT };
  }
  const shared = usage.sharedWith.map((machine) => machine.machineName);
  const reporter = usage.reportedByMachineName;
  return {
    kind: "known",
    windows: [
      windowView(FIVE_HOUR, usage.fiveHour, now, timeZone),
      windowView(WEEKLY, usage.sevenDay, now, timeZone),
    ],
    ageText: usage.capturedAt ? `updated ${ageText(usage.capturedAt, now)}` : null,
    sharedText:
      shared.length === 0
        ? null
        : `Shared with ${shared.join(", ")}${reporter ? ` · reported by ${reporter}` : ""}`,
  };
}

/**
 * The text beside a window's bar in the compact cell: "47%", "reset" once the
 * reset time has passed, or null for a window the machine did not report,
 * which the cell shows as a dash.
 */
export function windowValueText(view: UsageWindowView): string | null {
  if (view.kind === "used") return view.percentageText;
  if (view.kind === "reset") return RESET_VALUE_TEXT;
  return null;
}

/** The window's line in the compact cell's tooltip: "5-hour: 47%, resets 14:30, in 2 h 05 min". */
export function windowDetailText(view: UsageWindowView): string {
  if (view.kind === "used") return `${view.label}: ${view.percentageText}, ${view.resetText}`;
  if (view.kind === "reset") return `${view.label}: ${view.resetText}`;
  return `${view.label}: ${NOT_REPORTED}`;
}

/**
 * The compact cell keeps one short row per window; when each resets, how old
 * the snapshot is and which machines share the account move into its tooltip,
 * one line each.
 */
export function usageDetailLines(view: KnownUsageView): string[] {
  const lines = view.windows.map(windowDetailText);
  if (view.ageText !== null)
    lines.push(view.ageText.charAt(0).toUpperCase() + view.ageText.slice(1));
  if (view.sharedText !== null) lines.push(view.sharedText);
  return lines;
}

/** The usage cell recomputes countdowns, ages and passed resets this often. */
export const USAGE_TICK_MS = 30_000;
