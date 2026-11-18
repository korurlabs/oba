"use client";

import { useCallback, useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { addDays, differenceInCalendarDays, isSameDay } from "date-fns";
import type { MilestoneListResponse } from "@/features/milestones/schemas";
import { xToDate, type MilestoneDates, type RoadmapWindow } from "../lib/roadmap-layout";

/** Which part of a marker is dragged: the whole bar or diamond, its start end or its target end. */
export type DragMode = "move" | "start" | "end";

export type DragPreview = {
  milestoneId: string;
  dates: MilestoneDates;
  /** Dropped and handed to the save; shown until the roadmap data changes. */
  committed: boolean;
};

/** Below this distance a press is a click that opens the milestone, not a drag. */
const DRAG_THRESHOLD_PX = 4;

/** Marks the element whose left edge is day 0 of the window. */
export const ROADMAP_GRID_ATTRIBUTE = "data-roadmap-grid";

type DragSession = {
  pointerId: number;
  milestone: MilestoneListResponse;
  mode: DragMode;
  grid: HTMLElement;
  originClientX: number;
  originGridX: number;
  dates: MilestoneDates;
  moved: boolean;
};

interface UseBarDragOptions {
  range: RoadmapWindow;
  /** Bumped whenever the roadmap data changes; a committed preview then gives way to it. */
  dataVersion: unknown;
  onCommit: (milestone: MilestoneListResponse, dates: MilestoneDates) => void;
}

/**
 * Drag to reschedule with pointer events: the bar body moves both dates by the
 * same number of days, an end handle moves one date (never past the other),
 * and a diamond moves its target. Positions snap to whole days. Alt+Left and
 * Alt+Right on a focused marker move it by one day.
 *
 * Pointer capture keeps the drag alive when the pointer leaves the bar, and
 * the grid's own position is read on every move so the drag stays correct
 * while the timeline scrolls.
 */
export function useBarDrag({ range, dataVersion, onCommit }: UseBarDragOptions) {
  const [preview, setPreview] = useState<DragPreview | null>(null);
  const session = useRef<DragSession | null>(null);
  const suppressClick = useRef(false);
  // Repeated Alt+Arrow presses build on the last preview, not on data the
  // optimistic update may not have written yet.
  const previewRef = useRef<DragPreview | null>(null);
  useEffect(() => {
    previewRef.current = preview;
  }, [preview]);

  // The saved dates arrive as new roadmap data (the optimistic patch, or the
  // rollback on failure); from then on the data draws the marker.
  useEffect(() => {
    setPreview((current) => (current?.committed ? null : current));
  }, [dataVersion]);

  const datesAt = useCallback(
    (s: DragSession, gridX: number): MilestoneDates => {
      const { startDate, targetDate } = s.dates;
      if (s.mode === "start" && startDate) {
        const day = xToDate(range, gridX);
        return { startDate: day > targetDate ? targetDate : day, targetDate };
      }
      if (s.mode === "end") {
        const day = xToDate(range, gridX);
        return { startDate, targetDate: startDate && day < startDate ? startDate : day };
      }
      const delta = differenceInCalendarDays(xToDate(range, gridX), xToDate(range, s.originGridX));
      return {
        startDate: startDate ? addDays(startDate, delta) : null,
        targetDate: addDays(targetDate, delta),
      };
    },
    [range],
  );

  const pointerHandlers = useCallback(
    (milestone: MilestoneListResponse, dates: MilestoneDates, mode: DragMode) => ({
      onPointerDown: (event: PointerEvent<HTMLElement>) => {
        if (event.button !== 0) return;
        const grid = event.currentTarget.closest<HTMLElement>(`[${ROADMAP_GRID_ATTRIBUTE}]`);
        if (!grid) return;
        // An end handle sits inside the bar; the bar must not start a move as well.
        event.stopPropagation();
        event.currentTarget.setPointerCapture(event.pointerId);
        session.current = {
          pointerId: event.pointerId,
          milestone,
          mode,
          grid,
          originClientX: event.clientX,
          originGridX: event.clientX - grid.getBoundingClientRect().left,
          dates,
          moved: false,
        };
      },
      onPointerMove: (event: PointerEvent<HTMLElement>) => {
        const s = session.current;
        if (!s || s.pointerId !== event.pointerId) return;
        if (!s.moved && Math.abs(event.clientX - s.originClientX) < DRAG_THRESHOLD_PX) return;
        s.moved = true;
        const next = datesAt(s, event.clientX - s.grid.getBoundingClientRect().left);
        setPreview((current) =>
          current && current.milestoneId === s.milestone.id && sameDates(current.dates, next)
            ? current
            : { milestoneId: s.milestone.id, dates: next, committed: false },
        );
      },
      onPointerUp: (event: PointerEvent<HTMLElement>) => {
        const s = session.current;
        if (!s || s.pointerId !== event.pointerId) return;
        session.current = null;
        if (!s.moved) return;
        // The browser still fires a click on release; it must not open the
        // sheet. A release that produces no click must not swallow the next one.
        suppressClick.current = true;
        window.setTimeout(() => {
          suppressClick.current = false;
        }, 0);
        const next = datesAt(s, event.clientX - s.grid.getBoundingClientRect().left);
        if (sameDates(next, s.dates)) {
          setPreview(null);
          return;
        }
        setPreview({ milestoneId: s.milestone.id, dates: next, committed: true });
        onCommit(s.milestone, next);
      },
      onPointerCancel: () => {
        session.current = null;
        setPreview(null);
      },
    }),
    [datesAt, onCommit],
  );

  const keyHandler = useCallback(
    (milestone: MilestoneListResponse, dates: MilestoneDates) =>
      (event: KeyboardEvent<HTMLElement>) => {
        if (!event.altKey || (event.key !== "ArrowLeft" && event.key !== "ArrowRight")) return;
        event.preventDefault();
        const delta = event.key === "ArrowLeft" ? -1 : 1;
        const current = previewRef.current;
        const base = current?.milestoneId === milestone.id ? current.dates : dates;
        const next: MilestoneDates = {
          startDate: base.startDate ? addDays(base.startDate, delta) : null,
          targetDate: addDays(base.targetDate, delta),
        };
        setPreview({ milestoneId: milestone.id, dates: next, committed: true });
        onCommit(milestone, next);
      },
    [onCommit],
  );

  /** True once after a drag, for the click that the release produces. */
  const consumeClick = useCallback(() => {
    const suppressed = suppressClick.current;
    suppressClick.current = false;
    return suppressed;
  }, []);

  return { preview, pointerHandlers, keyHandler, consumeClick };
}

function sameDates(a: MilestoneDates, b: MilestoneDates): boolean {
  const sameStart =
    a.startDate === null || b.startDate === null
      ? a.startDate === b.startDate
      : isSameDay(a.startDate, b.startDate);
  return sameStart && isSameDay(a.targetDate, b.targetDate);
}
