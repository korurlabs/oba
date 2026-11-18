/*
 * The pure half of the board pick list shared by Create sprint, Add existing
 * boards and the board target pickers: what a row shows, the search, which
 * rows are ticked and what Select all ticks. No React or native import, so
 * `bun test` covers it.
 */

/** A board as a row of the pick list. */
export interface BoardPickItem {
  id: string;
  name: string;
  prefix: string | null;
  color: string | null;
  /** Levels below the list's top level. */
  indent: number;
  /** True when the board cannot be picked; `note` then says why. */
  disabled: boolean;
  /** A line under the name: why the board cannot be picked, or a caution. */
  note: string | null;
  /** `warning` for a caution on a board that can still be picked, such as its running sprint. */
  noteTone: "muted" | "warning";
}

/**
 * Multi mode ticks any number of boards with checkboxes; single mode picks
 * one board with radios, such as the target of a move.
 */
export type BoardPickMode = "multi" | "single";

/** A row as the list draws it. */
export interface BoardPickRow {
  item: BoardPickItem;
  checked: boolean;
}

/**
 * The rows matching the search in their name or prefix, ignoring case, in
 * the list's order, each ticked when it is picked. A blank search keeps every
 * row. In single mode only the first picked id counts.
 */
export function boardPickRows(
  items: readonly BoardPickItem[],
  mode: BoardPickMode,
  picked: readonly string[],
  search: string
): BoardPickRow[] {
  const ticked: Set<string> = new Set(mode === "single" ? picked.slice(0, 1) : picked);
  const needle: string = search.trim().toLocaleLowerCase();
  return items
    .filter(
      (item) =>
        !needle ||
        item.name.toLocaleLowerCase().includes(needle) ||
        (item.prefix ?? "").toLocaleLowerCase().includes(needle)
    )
    .map((item) => ({ item, checked: ticked.has(item.id) }));
}

/** The boards Select all ticks: every board that can be picked, in list order. */
export function selectAllIds(items: readonly BoardPickItem[]): string[] {
  return items.filter((item) => !item.disabled).map((item) => item.id);
}
