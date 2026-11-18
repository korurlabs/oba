import type { UserPreferences } from "@oba/shared/features/users/schema";

import type { BoardRow } from "@/components/boards/boardModel";

/*
 * Pinned boards, kept in the signed-in user's `userPreferences` under
 * `sidebar`, the record Kaizen web's sidebar reads and writes
 * (`apps/kaizen/hooks/use-sidebar-board-preferences.ts`). The phone reads and
 * changes the same record the same way, so a board pinned on one shows
 * pinned on the other. Pure, so `bun test` covers it.
 */

/** The key of the sidebar record in `userPreferences`. */
export const SIDEBAR_PREFERENCES_KEY = "sidebar";

/** The sidebar record as Kaizen web stores it. */
export interface SidebarPreferences {
  /** Top-level boards that are not pinned, in the web sidebar's order. */
  boardOrder: string[];
  /** Pinned top-level boards. */
  pinnedBoardIds: string[];
  /** Child boards that are not pinned, per direct parent board. */
  childBoardOrder: Record<string, string[]>;
  /** Pinned child boards, per direct parent board. */
  pinnedChildBoardIds: Record<string, string[]>;
  /** Every pinned board, top-level and child, in the order the Pinned list shows them. */
  pinnedDisplayOrder: string[];
}

/** The board a pin is about: a child board's pin is kept under its direct parent. */
export type PinTarget = Pick<BoardRow, "id" | "parentId">;

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((id): id is string => typeof id === "string") : [];
}

function listsById(value: unknown): Record<string, string[]> {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const lists: Record<string, string[]> = {};
  for (const [id, list] of Object.entries(value as Record<string, unknown>)) {
    lists[id] = stringList(list);
  }
  return lists;
}

function rawSidebar(preferences: UserPreferences | null | undefined): Record<string, unknown> {
  const sidebar: unknown = preferences?.[SIDEBAR_PREFERENCES_KEY];
  return sidebar && typeof sidebar === "object" && !Array.isArray(sidebar)
    ? (sidebar as Record<string, unknown>)
    : {};
}

/** The saved sidebar record; empty lists when none is saved or a field has another shape. */
export function readSidebarPreferences(
  preferences: UserPreferences | null | undefined
): SidebarPreferences {
  const sidebar: Record<string, unknown> = rawSidebar(preferences);
  return {
    boardOrder: stringList(sidebar.boardOrder),
    pinnedBoardIds: stringList(sidebar.pinnedBoardIds),
    childBoardOrder: listsById(sidebar.childBoardOrder),
    pinnedChildBoardIds: listsById(sidebar.pinnedChildBoardIds),
    pinnedDisplayOrder: stringList(sidebar.pinnedDisplayOrder),
  };
}

/**
 * The whole preferences with the sidebar record replaced. `PUT /users/me`
 * replaces every preference, so the other keys (theme, chat order) are sent
 * back as they are, and so are sidebar fields this app does not know.
 */
export function withSidebarPreferences(
  preferences: UserPreferences | null | undefined,
  sidebar: SidebarPreferences
): UserPreferences {
  return {
    ...(preferences ?? {}),
    [SIDEBAR_PREFERENCES_KEY]: { ...rawSidebar(preferences), ...sidebar },
  };
}

/** True when the board is pinned. */
export function isBoardPinned(sidebar: SidebarPreferences, target: PinTarget): boolean {
  if (target.parentId === null) return sidebar.pinnedBoardIds.includes(target.id);
  return (sidebar.pinnedChildBoardIds[target.parentId] ?? []).includes(target.id);
}

/**
 * The record after pinning or unpinning the board, changed as the web
 * sidebar changes it: a pinned board leaves its unpinned order and joins the
 * end of the pinned order; an unpinned board leaves the pinned order and goes
 * back to the end of its unpinned order. Never mutates the input.
 */
export function toggleBoardPin(sidebar: SidebarPreferences, target: PinTarget): SidebarPreferences {
  const pinned: boolean = isBoardPinned(sidebar, target);
  const withoutTarget = (ids: readonly string[]): string[] => ids.filter((id) => id !== target.id);
  const displayOrder: string[] = pinned
    ? withoutTarget(sidebar.pinnedDisplayOrder)
    : [...withoutTarget(sidebar.pinnedDisplayOrder), target.id];

  if (target.parentId === null) {
    return {
      ...sidebar,
      pinnedBoardIds: pinned
        ? withoutTarget(sidebar.pinnedBoardIds)
        : [...sidebar.pinnedBoardIds, target.id],
      boardOrder: pinned
        ? sidebar.boardOrder.includes(target.id)
          ? sidebar.boardOrder
          : [...sidebar.boardOrder, target.id]
        : withoutTarget(sidebar.boardOrder),
      pinnedDisplayOrder: displayOrder,
    };
  }

  const parentId: string = target.parentId;
  const pinnedChildren: string[] = sidebar.pinnedChildBoardIds[parentId] ?? [];
  const nextPinnedChildren: string[] = pinned
    ? withoutTarget(pinnedChildren)
    : [...pinnedChildren, target.id];
  const pinnedChildBoardIds: Record<string, string[]> = { ...sidebar.pinnedChildBoardIds };
  if (nextPinnedChildren.length > 0) pinnedChildBoardIds[parentId] = nextPinnedChildren;
  else delete pinnedChildBoardIds[parentId];

  const childOrder: string[] = sidebar.childBoardOrder[parentId] ?? [];
  return {
    ...sidebar,
    pinnedChildBoardIds,
    childBoardOrder: {
      ...sidebar.childBoardOrder,
      [parentId]: pinned
        ? childOrder.includes(target.id)
          ? childOrder
          : [...childOrder, target.id]
        : withoutTarget(childOrder),
    },
    pinnedDisplayOrder: displayOrder,
  };
}

/**
 * The pinned boards in the order the web's Pinned list shows them: the saved
 * display order first, then any other pinned board, top-level ones before
 * child ones. A pin counts only while the board is still where it was pinned
 * (top level, or under the same parent), as on the web.
 */
export function pinnedBoardRows(
  rows: readonly BoardRow[],
  sidebar: SidebarPreferences
): BoardRow[] {
  const byId: Map<string, BoardRow> = new Map(rows.map((row) => [row.id, row]));
  const pinned: Map<string, BoardRow> = new Map();
  for (const id of sidebar.pinnedBoardIds) {
    const row: BoardRow | undefined = byId.get(id);
    if (row && row.parentId === null) pinned.set(id, row);
  }
  for (const [parentId, ids] of Object.entries(sidebar.pinnedChildBoardIds)) {
    for (const id of ids) {
      const row: BoardRow | undefined = byId.get(id);
      if (row && row.parentId === parentId) pinned.set(id, row);
    }
  }
  const ordered: BoardRow[] = [];
  const placed: Set<string> = new Set();
  for (const id of [...sidebar.pinnedDisplayOrder, ...pinned.keys()]) {
    const row: BoardRow | undefined = pinned.get(id);
    if (!row || placed.has(id)) continue;
    placed.add(id);
    ordered.push(row);
  }
  return ordered;
}
