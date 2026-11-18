import * as React from "react";

import type { TicketScope } from "@/components/boards/boardModel";
import { pruneSelection, toggleSelection } from "@/components/home/homeModel";

/**
 * The list a selection is made in: the sprint the view shows or the backlog.
 * One group is selected at a time, as each list on the web has its own
 * selection and bulk move.
 */
export type SelectionGroup = "sprint" | "backlog";

/** Selection mode: its group (null until the first ticket is picked) and the picked ticket ids. */
export interface SelectionState {
  group: SelectionGroup | null;
  ids: ReadonlySet<string>;
}

/** The group of a list's place on the board. */
export function selectionGroupOf(scope: TicketScope): SelectionGroup {
  return "sprintId" in scope ? "sprint" : "backlog";
}

/** Selection mode started on `group` (or on no group yet) with `ids` picked. */
export function startSelection(
  group: SelectionGroup | null,
  ids: readonly string[]
): SelectionState {
  return { group, ids: new Set(ids) };
}

/**
 * Whether a row of `group` can be picked: always while nothing is picked,
 * else only in the group that holds the selection.
 */
export function selectableIn(state: SelectionState, group: SelectionGroup): boolean {
  return state.ids.size === 0 || state.group === group;
}

/**
 * Picks or unpicks a ticket of `group`. The first pick decides the group; a
 * row of the other group cannot be picked while the selection holds tickets.
 */
export function toggleInSelection(
  state: SelectionState,
  group: SelectionGroup,
  id: string
): SelectionState {
  if (!selectableIn(state, group)) return state;
  return { group, ids: toggleSelection(state.group === group ? state.ids : new Set(), id) };
}

/**
 * "Select all" over the rows `group` shows; "Select none" when every one of
 * them is already picked.
 */
export function selectAllInGroup(
  state: SelectionState,
  group: SelectionGroup,
  rowIds: readonly string[]
): SelectionState {
  if (!selectableIn(state, group)) return state;
  if (allPicked(state, group, rowIds)) return { group, ids: new Set() };
  return { group, ids: new Set(rowIds) };
}

/** Every row of `group` is picked, so its header offers "Select none". */
export function allPicked(
  state: SelectionState,
  group: SelectionGroup,
  rowIds: readonly string[]
): boolean {
  return state.group === group && rowIds.length > 0 && rowIds.every((id) => state.ids.has(id));
}

/** Drops the picked tickets that left the group's list, after a refetch or a move. */
export function pruneToListed(state: SelectionState, listedIds: readonly string[]): SelectionState {
  if (state.ids.size === 0) return state;
  const ids: Set<string> = pruneSelection(state.ids, listedIds);
  return ids.size === state.ids.size ? state : { group: state.group, ids };
}

/** What a board view reads and calls while selection mode is on. */
export interface BoardSelection {
  state: SelectionState;
  toggle: (group: SelectionGroup, id: string) => void;
  toggleAll: (group: SelectionGroup, rowIds: readonly string[]) => void;
}

/**
 * Selection mode of a board screen: null while it is off. `start` turns it on
 * with the given tickets picked; `prune` keeps it to the tickets still listed.
 */
export function useTicketSelection() {
  const [state, setState] = React.useState<SelectionState | null>(null);

  const start = React.useCallback(
    (group: SelectionGroup | null, ids: readonly string[]) => setState(startSelection(group, ids)),
    []
  );
  const end = React.useCallback(() => setState(null), []);
  const toggle = React.useCallback(
    (group: SelectionGroup, id: string) =>
      setState((current) => (current ? toggleInSelection(current, group, id) : current)),
    []
  );
  const toggleAll = React.useCallback(
    (group: SelectionGroup, rowIds: readonly string[]) =>
      setState((current) => (current ? selectAllInGroup(current, group, rowIds) : current)),
    []
  );
  const prune = React.useCallback(
    (group: SelectionGroup, listedIds: readonly string[]) =>
      setState((current) =>
        current && current.group === group ? pruneToListed(current, listedIds) : current
      ),
    []
  );

  const selection: BoardSelection | null = React.useMemo(
    () => (state ? { state, toggle, toggleAll } : null),
    [state, toggle, toggleAll]
  );

  return { state, selection, start, end, prune };
}
