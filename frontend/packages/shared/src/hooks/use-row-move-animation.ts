"use client";

import { useCallback, useLayoutEffect, useRef } from "react";
import { prefersReducedMotion } from "@oba/shared/lib/reduced-motion";

const ROW_MOVE_DURATION_MS = 200;
const ROW_MOVE_EASING = "cubic-bezier(0.2, 0, 0, 1)";
/** Offsets below this are rounding noise, not a move. */
const MIN_MOVE_PX = 1;

export interface RowMoveAnimationOptions {
  /** Off: rows jump to their new place, as before. */
  enabled: boolean;
  /**
   * Ids of the table's rows in display order, expanded child rows included so
   * they travel with their parent. Rows a virtualized table has not mounted
   * are ignored. Empty while the table loads or has no rows.
   */
  rowIds: readonly string[];
  /**
   * dnd-kit places the rows in this commit: a drag runs, or the commit shows
   * the order the user just dropped. The commit after a skipped one is skipped
   * too, because it is the commit in which a drag ends, and dnd-kit animates
   * the layout changes of that commit itself.
   */
  skip: boolean;
}

export type RowMoveRef = (node: HTMLTableRowElement | null) => void;

/**
 * Slides table rows to their new place when the data changes their order
 * (another user's move, a refetch, a rollback, a column sort), so a row that
 * changes place can be followed with the eye instead of jumping.
 *
 * FLIP: every commit records each rendered row's `offsetTop`. When the order
 * of `rowIds` changed since the last commit, each row that was rendered before
 * and now sits elsewhere is played from its old offset back to zero.
 *
 * The animation runs on the `translate` property, never on `transform`:
 * dnd-kit writes `transform` inline on sortable rows, and the two properties
 * compose instead of overwriting each other. `offsetTop` ignores both, so the
 * recorded offsets are layout positions, independent of scrolling and of a
 * slide still in flight.
 *
 * @returns a ref factory: pass `rowRef(id)` as the ref of the row with that id.
 * The callback per id is stable across renders, so React does not detach and
 * re-attach the row on every commit.
 */
export function useRowMoveAnimation({
  enabled,
  rowIds,
  skip,
}: RowMoveAnimationOptions): (id: string) => RowMoveRef {
  const nodes = useRef(new Map<string, HTMLTableRowElement>());
  const refCallbacks = useRef(new Map<string, RowMoveRef>());
  const animations = useRef(new Map<string, Animation>());
  const previousOffsets = useRef(new Map<string, number>());
  const previousOrder = useRef<readonly string[]>([]);
  const previousSkip = useRef(false);

  const rowRef = useCallback((id: string): RowMoveRef => {
    const existing = refCallbacks.current.get(id);
    if (existing) return existing;
    const callback: RowMoveRef = (node) => {
      if (node) nodes.current.set(id, node);
      else nodes.current.delete(id);
    };
    refCallbacks.current.set(id, callback);
    return callback;
  }, []);

  // No dependency list on purpose: the offsets must be recorded after every
  // commit (expanding a row or a refetch moves rows without reordering them),
  // so the next reorder starts each row from where it really was.
  useLayoutEffect(() => {
    if (!enabled) {
      previousOffsets.current = new Map();
      previousOrder.current = [];
      previousSkip.current = false;
      return;
    }

    const offsets = new Map<string, number>();
    for (const id of rowIds) {
      const node = nodes.current.get(id);
      if (node) offsets.set(id, node.offsetTop);
    }

    const shouldAnimate =
      !skip &&
      !previousSkip.current &&
      previousOrder.current.length > 0 &&
      !sameOrder(previousOrder.current, rowIds) &&
      !prefersReducedMotion();

    if (shouldAnimate) {
      for (const [id, offset] of offsets) {
        const before = previousOffsets.current.get(id);
        const node = nodes.current.get(id);
        if (before === undefined || !node) continue;
        const delta = before + takeInFlightOffset(node, animations.current.get(id)) - offset;
        if (Math.abs(delta) < MIN_MOVE_PX) continue;
        const animation = node.animate([{ translate: `0 ${delta}px` }, { translate: "0 0" }], {
          duration: ROW_MOVE_DURATION_MS,
          easing: ROW_MOVE_EASING,
        });
        animations.current.set(id, animation);
      }
    }

    // Drop the bookkeeping of rows that left the data, so the maps do not
    // grow with every ticket a long-lived table has ever shown.
    const current = new Set(rowIds);
    for (const map of [refCallbacks.current, animations.current]) {
      for (const id of map.keys()) {
        if (!current.has(id)) map.delete(id);
      }
    }

    previousOffsets.current = offsets;
    previousOrder.current = rowIds;
    previousSkip.current = skip;
  });

  return rowRef;
}

function sameOrder(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i += 1) {
    if (a[i] !== b[i]) return false;
  }
  return true;
}

/**
 * Where a slide still in flight has the row right now, relative to its layout
 * position, in pixels; the slide is cancelled so the next one starts from that
 * spot instead of jumping. Zero when no slide runs.
 */
function takeInFlightOffset(node: HTMLTableRowElement, animation: Animation | undefined): number {
  if (!animation || animation.playState !== "running") return 0;
  // The computed `translate` is "none", "<x>" or "<x> <y>"; only y is used.
  const y = getComputedStyle(node).translate.split(" ")[1];
  animation.cancel();
  const parsed = y === undefined ? 0 : Number.parseFloat(y);
  return Number.isFinite(parsed) ? parsed : 0;
}
