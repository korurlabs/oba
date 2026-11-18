/*
 * The pure half of a board row on the Boards tab root: which facts its
 * one-line subtitle names on each platform, and the icon name a board
 * carries. No React or native import, so `bun test` covers it.
 */

/** One piece of a board row's subtitle; the row turns each into text. */
export type BoardSubtitlePart =
  | { kind: "key"; prefix: string }
  /** The parent board: "In X" on iOS, "Child of X" on Android. */
  | { kind: "parent"; name: string }
  | { kind: "childCount"; count: number }
  | { kind: "sprint"; name: string }
  | { kind: "daysLeft"; text: string }
  | { kind: "noSprint" }
  | { kind: "open"; count: number };

/** What a board row knows when it builds its subtitle. */
export interface BoardSubtitleFacts {
  prefix: string | null;
  /** Set for a child board. */
  parentName: string | null;
  childCount: number;
  /** The running sprint: null when the board has none, undefined while it loads. */
  sprint: { name: string; daysLeft: string | null } | null | undefined;
  /** Open tickets; undefined while they load. */
  openCount: number | undefined;
}

function sprintParts(
  sprint: BoardSubtitleFacts["sprint"],
  withDaysLeft: boolean
): BoardSubtitlePart[] {
  if (sprint === undefined) return [];
  if (sprint === null) return [{ kind: "noSprint" }];
  const parts: BoardSubtitlePart[] = [{ kind: "sprint", name: sprint.name }];
  if (withDaysLeft && sprint.daysLeft) parts.push({ kind: "daysLeft", text: sprint.daysLeft });
  return parts;
}

/**
 * The parts of a board row's subtitle, which fits on one line. A row names
 * only what helps pick the board in its section:
 *
 * - iOS Recent: a child board names its parent and sprint; another board its
 *   sprint and the days left.
 * - iOS All boards: a child board names its parent; a board with child boards
 *   its key and their count; another board its key and sprint. The open
 *   count is the row value there, not part of the subtitle.
 * - Android Recent: a child board names its parent; another board its key,
 *   sprint and the days left.
 * - Android All boards: the key and the open tickets.
 *
 * Facts still loading are left out rather than shown as a placeholder.
 */
export function boardSubtitleParts(
  platform: "ios" | "android",
  variant: "recent" | "all",
  facts: BoardSubtitleFacts
): BoardSubtitlePart[] {
  const key: BoardSubtitlePart[] = facts.prefix ? [{ kind: "key", prefix: facts.prefix }] : [];
  const parent: BoardSubtitlePart[] = facts.parentName
    ? [{ kind: "parent", name: facts.parentName }]
    : [];

  if (platform === "android") {
    if (variant === "all") {
      return [
        ...key,
        ...(facts.openCount === undefined
          ? []
          : [{ kind: "open", count: facts.openCount } as const]),
      ];
    }
    return facts.parentName ? parent : [...key, ...sprintParts(facts.sprint, true)];
  }

  if (variant === "recent") {
    return facts.parentName
      ? [...parent, ...sprintParts(facts.sprint, false)]
      : sprintParts(facts.sprint, true);
  }
  if (facts.parentName) return parent;
  if (facts.childCount > 0) return [...key, { kind: "childCount", count: facts.childCount }];
  return [...key, ...sprintParts(facts.sprint, false)];
}

/**
 * The lucide icon name a board's `icon` field names, in kebab case
 * ("Building2", "building_2" and "building-2" all give "building-2"); null
 * when the field is empty. Whether the name maps to a bundled icon is the
 * row's decision.
 */
export function boardIconKey(icon: string | null | undefined): string | null {
  const trimmed: string = (icon ?? "").trim().replace(/^lucide[:-]/i, "");
  if (!trimmed) return null;
  return trimmed
    .replace(/([a-z0-9])([A-Z])/g, "$1-$2")
    .replace(/([a-zA-Z])(\d)/g, "$1-$2")
    .replace(/[\s_]+/g, "-")
    .replace(/-+/g, "-")
    .toLowerCase();
}
