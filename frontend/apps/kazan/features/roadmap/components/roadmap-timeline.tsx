"use client";

import {
  useCallback,
  useEffect,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
  type Ref,
} from "react";
import { format, startOfDay, type Locale } from "date-fns";
import { AlertTriangle, ChevronRight } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { cn } from "@oba/ui/lib/utils";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@oba/ui/components/tooltip";
import type { MilestoneListResponse, RoadmapBoardResponse } from "@/features/milestones/schemas";
import { toDateOnly } from "@oba/shared/lib/date-only";
import { getDateFnsLocale } from "@oba/shared/lib/date-fns-locale";
import { MILESTONE_HEALTH_STYLES } from "@/features/milestones/lib/milestone-format";
import type { RoadmapZoom } from "../hooks/use-roadmap-params";
import { ROADMAP_GRID_ATTRIBUTE, useBarDrag, type DragPreview } from "../hooks/use-bar-drag";
import { useRescheduleMilestone } from "../hooks/use-reschedule-milestone";
import {
  DIAMOND_PX,
  computeWindow,
  dateToX,
  markerInX,
  markerOutX,
  milestoneDates,
  periodIndexOf,
  placeMarker,
  sortMilestones,
  type MarkerPlacement,
  type MilestoneDates,
} from "../lib/roadmap-layout";
import { RoadmapDependencyLines, type DependencyLine } from "./roadmap-dependency-lines";
import { RoadmapInlineCreate } from "./roadmap-inline-create";
import { RoadmapTicketRows } from "./roadmap-ticket-rows";

const NAME_COLUMN_WIDTH_PX = 256;
const NAME_COLUMN_NARROW_WIDTH_PX = 160;
const NARROW_VIEWPORT_PX = 640;
const NAME_COLUMN_MIN_WIDTH_PX = 140;
const NAME_COLUMN_MAX_WIDTH_PX = 560;
/** The timeline keeps at least this much of the viewport when the name column is widened. */
const TIMELINE_MIN_VISIBLE_PX = 160;
/** One arrow-key press on the column divider changes the width by this much. */
const NAME_COLUMN_KEY_STEP_PX = 16;
const NAME_COLUMN_STORAGE_KEY = "kazan-roadmap-name-column-width";
const HEADER_HEIGHT_PX = 40;
const GROUP_ROW_HEIGHT_PX = 36;
const MILESTONE_ROW_HEIGHT_PX = 40;
const BAR_HEIGHT_PX = 20;
/** Today sits this far into the visible timeline when it is scrolled into view. */
const TODAY_VIEWPORT_FRACTION = 1 / 3;
/** Bar colour for a milestone without one (a board without a colour). */
const NEUTRAL_BAR_COLOR = "var(--muted-foreground)";

/** Alternate rows are a little darker than the card, opaque so the sticky column hides what scrolls under it. */
const ROW_STYLES: [CSSProperties, CSSProperties] = [
  { backgroundColor: "var(--card)" },
  { backgroundColor: "color-mix(in oklab, var(--muted) 55%, var(--card))" },
];

export type RoadmapTimelineHandle = {
  scrollToToday: () => void;
};

interface RoadmapTimelineProps {
  /** Lanes after filtering. */
  boards: RoadmapBoardResponse[];
  /** Board rows appear only when the roadmap has child-board lanes. */
  grouped: boolean;
  zoom: RoadmapZoom;
  /** The board whose roadmap this is; inline create adds milestones to it. */
  pageBoardId: string;
  /** Boards on which the viewer is OWNER or ADMIN and may drag and create. */
  manageableBoardIds: ReadonlySet<string>;
  onSelectMilestone: (milestone: MilestoneListResponse) => void;
  ref?: Ref<RoadmapTimelineHandle>;
}

type MilestoneRowModel = {
  milestone: MilestoneListResponse;
  dates: MilestoneDates;
  placement: MarkerPlacement;
};

type GroupModel = {
  board: RoadmapBoardResponse;
  rows: MilestoneRowModel[];
};

/**
 * Milestones on a horizontally scrolling time axis, one row per milestone in
 * target-date order. A milestone with a start date is a bar in its own colour
 * from start to target, one without is a diamond on its target date; health
 * is a dot with text in the tooltip and the accessible label. A row expands
 * into its tickets, dependencies are drawn as connectors, and admins drag a
 * bar (or its ends) to reschedule it. The name column and the period header
 * stay in place while the grid scrolls.
 */
export function RoadmapTimeline({
  boards,
  grouped,
  zoom,
  pageBoardId,
  manageableBoardIds,
  onSelectMilestone,
  ref,
}: RoadmapTimelineProps) {
  const locale = useLocale();
  const t = useTranslations("roadmap");
  const dateLocale = getDateFnsLocale(locale);
  const scrollRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);

  // "Today" and the viewport width depend on the viewer's browser, which the
  // server render does not know, so both are read only after mount.
  const [today, setToday] = useState<Date | null>(null);
  const [viewportWidth, setViewportWidth] = useState(0);
  useEffect(() => {
    setToday(startOfDay(new Date()));
    const element = scrollRef.current;
    if (!element) return;
    const observer = new ResizeObserver(() => setViewportWidth(element.clientWidth));
    observer.observe(element);
    setViewportWidth(element.clientWidth);
    return () => observer.disconnect();
  }, []);

  const defaultNameColumnWidth =
    viewportWidth > 0 && viewportWidth < NARROW_VIEWPORT_PX
      ? NAME_COLUMN_NARROW_WIDTH_PX
      : NAME_COLUMN_WIDTH_PX;
  const nameColumnMaxWidth =
    viewportWidth > 0
      ? Math.max(
          NAME_COLUMN_MIN_WIDTH_PX,
          Math.min(NAME_COLUMN_MAX_WIDTH_PX, viewportWidth - TIMELINE_MIN_VISIBLE_PX),
        )
      : NAME_COLUMN_MAX_WIDTH_PX;
  const nameColumn = useNameColumnWidth(defaultNameColumnWidth, nameColumnMaxWidth);
  const nameColumnWidth = nameColumn.width;
  const timelineViewport = Math.max(0, viewportWidth - nameColumnWidth);

  const range = useMemo(
    () => computeWindow(boards, zoom, today, timelineViewport),
    [boards, zoom, today, timelineViewport],
  );
  const todayX = today ? dateToX(range, today, "centre") : null;
  const currentPeriod = today ? periodIndexOf(range, today) : null;

  const reschedule = useRescheduleMilestone(pageBoardId);
  const { mutate: rescheduleMutate } = reschedule;
  const onCommit = useCallback(
    (milestone: MilestoneListResponse, dates: MilestoneDates) =>
      rescheduleMutate({
        milestone,
        startDate: dates.startDate ? toDateOnly(dates.startDate) : null,
        targetDate: toDateOnly(dates.targetDate),
      }),
    [rescheduleMutate],
  );
  const drag = useBarDrag({ range, dataVersion: boards, onCommit });

  const groups = useMemo(
    () => buildGroups(boards, range, drag.preview),
    [boards, range, drag.preview],
  );

  // The milestone whose marker is hovered or focused; its connectors are highlighted.
  const [activeMilestoneId, setActiveMilestoneId] = useState<string | null>(null);

  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(() => new Set());
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set());
  const toggle = (setter: typeof setCollapsed, id: string) =>
    setter((previous) => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  // Row centres come from the DOM: expanded rows hold a ticket list whose
  // length is known only once it has loaded.
  const [geometry, setGeometry] = useState<{ height: number; centres: Map<string, number> }>(
    () => ({ height: 0, centres: new Map() }),
  );
  const measure = useCallback(() => {
    const body = bodyRef.current;
    if (!body) return;
    const centres = new Map<string, number>();
    body.querySelectorAll<HTMLElement>("[data-milestone-row]").forEach((row) => {
      const id = row.dataset.milestoneRow;
      if (id) centres.set(id, row.offsetTop + row.offsetHeight / 2);
    });
    const height = body.offsetHeight;
    setGeometry((previous) =>
      previous.height === height && sameCentres(previous.centres, centres)
        ? previous
        : { height, centres },
    );
  }, []);
  useLayoutEffect(measure, [measure, groups, collapsed, expanded]);
  useEffect(() => {
    const body = bodyRef.current;
    if (!body) return;
    const observer = new ResizeObserver(measure);
    observer.observe(body);
    return () => observer.disconnect();
  }, [measure]);

  const lines = useMemo(() => {
    const visible = new Map<string, MilestoneRowModel>();
    for (const group of groups) {
      if (collapsed.has(group.board.id)) continue;
      for (const row of group.rows) visible.set(row.milestone.id, row);
    }
    const result: DependencyLine[] = [];
    for (const row of visible.values()) {
      const toY = geometry.centres.get(row.milestone.id);
      if (toY === undefined) continue;
      for (const dependency of row.milestone.dependsOn) {
        const from = visible.get(dependency.milestoneId);
        const fromY = geometry.centres.get(dependency.milestoneId);
        if (!from || fromY === undefined) continue;
        // Same rule as the server, on the dates on screen, so a drag shows
        // the conflict it would cause before it is dropped.
        const dependentStart = row.dates.startDate ?? row.dates.targetDate;
        result.push({
          key: `${dependency.milestoneId}->${row.milestone.id}`,
          fromId: dependency.milestoneId,
          fromName: from.milestone.name,
          toId: row.milestone.id,
          toName: row.milestone.name,
          fromX: markerOutX(from.placement),
          fromY,
          toX: markerInX(row.placement),
          toY,
          conflict: dependentStart < from.dates.targetDate,
        });
      }
    }
    return result;
  }, [groups, collapsed, geometry]);

  const scrollToToday = useCallback(
    (behavior: ScrollBehavior) => {
      const element = scrollRef.current;
      if (!element || todayX === null) return;
      element.scrollTo({
        left: Math.max(0, todayX - timelineViewport * TODAY_VIEWPORT_FRACTION),
        behavior,
      });
    },
    [todayX, timelineViewport],
  );

  useImperativeHandle(ref, () => ({ scrollToToday: () => scrollToToday("smooth") }), [
    scrollToToday,
  ]);

  // Bring today into view once on first load and again after a zoom change,
  // as soon as both today and the viewport width are known.
  const scrolledForZoom = useRef<RoadmapZoom | null>(null);
  useLayoutEffect(() => {
    if (todayX === null || timelineViewport === 0 || scrolledForZoom.current === zoom) return;
    scrolledForZoom.current = zoom;
    scrollToToday("auto");
  }, [zoom, todayX, timelineViewport, scrollToToday]);

  const gridBackground: CSSProperties = {
    backgroundImage: "linear-gradient(to right, var(--border) 1px, transparent 1px)",
    backgroundSize: `${range.columnWidth}px 100%`,
  };

  const canCreate = manageableBoardIds.has(pageBoardId);
  let stripeCounter = 0;

  return (
    <TooltipProvider delayDuration={150}>
      <div className="relative h-full">
        <div
          ref={scrollRef}
          className="border-border bg-card relative h-full overflow-auto rounded-md border"
        >
          <div
            className="relative flex min-h-full flex-col"
            style={{ width: nameColumnWidth + range.width }}
          >
            {/* Header */}
            <div
              className="bg-card border-border sticky top-0 z-30 flex shrink-0 border-b"
              style={{ height: HEADER_HEIGHT_PX }}
            >
              <div
                className="bg-card border-border text-muted-foreground sticky left-0 z-10 flex shrink-0 items-center border-r px-3 text-xs font-medium"
                style={{ width: nameColumnWidth }}
              >
                {t("columnHeader")}
              </div>
              <div className="relative flex" style={{ width: range.width }}>
                {range.periods.map((period, index) => (
                  <div
                    key={period.start.toISOString()}
                    className={cn(
                      "border-border text-muted-foreground flex shrink-0 items-center justify-center border-l text-[11px] font-medium tracking-wide uppercase first:border-l-0",
                      index === currentPeriod && "text-foreground",
                    )}
                    style={{ width: range.columnWidth }}
                  >
                    {periodLabel(period.start, index, zoom, dateLocale)}
                  </div>
                ))}
                {todayX !== null && today && (
                  <span
                    className="border-t-primary absolute bottom-0 h-0 w-0 -translate-x-1/2 border-x-[5px] border-t-[6px] border-x-transparent"
                    style={{ left: todayX }}
                    title={t("todayMarker", { date: format(today, "PP", { locale: dateLocale }) })}
                    aria-hidden
                  />
                )}
              </div>
            </div>

            {/* Body */}
            <div ref={bodyRef} className="relative flex flex-1 flex-col">
              {groups.map(({ board, rows }) => {
                const isCollapsed = grouped && collapsed.has(board.id);
                return (
                  <div key={board.id} role="group" aria-label={board.name}>
                    {grouped && (
                      <div className="bg-muted flex" style={{ height: GROUP_ROW_HEIGHT_PX }}>
                        <button
                          type="button"
                          aria-expanded={!isCollapsed}
                          onClick={() => toggle(setCollapsed, board.id)}
                          className="bg-muted border-border hover:bg-accent focus-visible:ring-ring sticky left-0 z-20 flex shrink-0 items-center gap-2 border-r px-2 text-left focus-visible:ring-2 focus-visible:outline-none focus-visible:ring-inset"
                          style={{ width: nameColumnWidth }}
                        >
                          <ChevronRight
                            className={cn(
                              "text-muted-foreground h-4 w-4 shrink-0 transition-transform",
                              !isCollapsed && "rotate-90",
                            )}
                            aria-hidden
                          />
                          <span
                            className="h-2.5 w-2.5 shrink-0 rounded-full"
                            style={{ backgroundColor: board.color ?? NEUTRAL_BAR_COLOR }}
                            aria-hidden
                          />
                          <span
                            className="text-foreground min-w-0 truncate text-sm font-semibold"
                            title={board.name}
                          >
                            {board.name}
                          </span>
                          <span className="text-muted-foreground ml-auto shrink-0 text-xs">
                            {rows.length}
                          </span>
                        </button>
                        <div className="flex-1" />
                      </div>
                    )}

                    {!isCollapsed && grouped && rows.length === 0 && (
                      <div className="flex" style={{ height: MILESTONE_ROW_HEIGHT_PX }}>
                        <div
                          className="bg-card border-border text-muted-foreground sticky left-0 z-20 flex shrink-0 items-center border-r pr-3 pl-8 text-xs"
                          style={{ width: nameColumnWidth }}
                        >
                          {t("noMilestonesInLane")}
                        </div>
                      </div>
                    )}

                    {!isCollapsed &&
                      rows.map((row) => {
                        const rowStyle = ROW_STYLES[(stripeCounter++ % 2) as 0 | 1];
                        const isExpanded = expanded.has(row.milestone.id);
                        return (
                          <div key={row.milestone.id}>
                            <MilestoneRow
                              row={row}
                              rowStyle={rowStyle}
                              indent={grouped}
                              nameColumnWidth={nameColumnWidth}
                              expanded={isExpanded}
                              onToggleExpanded={() => toggle(setExpanded, row.milestone.id)}
                              canDrag={manageableBoardIds.has(row.milestone.boardId)}
                              drag={drag}
                              dateLocale={dateLocale}
                              onSelect={onSelectMilestone}
                              onActiveChange={setActiveMilestoneId}
                            />
                            {isExpanded && (
                              <RoadmapTicketRows
                                milestoneId={row.milestone.id}
                                nameColumnWidth={nameColumnWidth}
                                rowStyle={rowStyle}
                              />
                            )}
                          </div>
                        );
                      })}
                  </div>
                );
              })}

              {canCreate && (
                <RoadmapInlineCreate
                  boardId={pageBoardId}
                  nameColumnWidth={nameColumnWidth}
                  height={MILESTONE_ROW_HEIGHT_PX}
                  rowStyle={ROW_STYLES[(stripeCounter % 2) as 0 | 1]}
                />
              )}

              {/* The name column continues below the last row so the overlays scrolling under it stay hidden. */}
              <div className="flex flex-1" aria-hidden>
                <div
                  className="bg-card border-border sticky left-0 z-20 shrink-0 border-r"
                  style={{ width: nameColumnWidth }}
                />
              </div>

              {/* Grid lines, the current period and today, over the row backgrounds and under the markers. */}
              <div
                className="pointer-events-none absolute inset-y-0 z-[1]"
                style={{ left: nameColumnWidth, width: range.width, ...gridBackground }}
                aria-hidden
              >
                {currentPeriod !== null && (
                  <div
                    className="bg-foreground/[0.04] absolute inset-y-0"
                    style={{ left: currentPeriod * range.columnWidth, width: range.columnWidth }}
                  />
                )}
                {todayX !== null && (
                  <div className="bg-primary absolute inset-y-0 w-px" style={{ left: todayX }} />
                )}
              </div>

              <div
                className="pointer-events-none absolute inset-y-0"
                style={{ left: nameColumnWidth, width: range.width }}
              >
                <RoadmapDependencyLines
                  lines={lines}
                  width={range.width}
                  height={geometry.height}
                  rowHeight={MILESTONE_ROW_HEIGHT_PX}
                  activeMilestoneId={activeMilestoneId}
                />
              </div>
            </div>
          </div>
        </div>
        <NameColumnResizer
          width={nameColumnWidth}
          minWidth={NAME_COLUMN_MIN_WIDTH_PX}
          maxWidth={nameColumnMaxWidth}
          label={t("columnResize")}
          onResize={nameColumn.preview}
          onCommit={nameColumn.commit}
          onReset={nameColumn.reset}
        />
      </div>
    </TooltipProvider>
  );
}

function clampWidth(width: number, min: number, max: number): number {
  return Math.round(Math.min(max, Math.max(min, width)));
}

/**
 * Width of the name column: the viewer's own choice, remembered in
 * localStorage, or the default for the viewport. Storage is read only after
 * mount, because the server render has no access to it. Storage can be
 * unavailable (private mode, blocked site data); the column then falls back to
 * the default and a chosen width lasts until the page is left.
 */
function useNameColumnWidth(defaultWidth: number, maxWidth: number) {
  const [chosenWidth, setChosenWidth] = useState<number | null>(null);

  useEffect(() => {
    let stored: string | null = null;
    try {
      stored = window.localStorage.getItem(NAME_COLUMN_STORAGE_KEY);
    } catch (error) {
      // The column keeps its default width, which is all the viewer loses.
      console.warn("[RoadmapTimeline] could not read the name column width", error);
      return;
    }
    const parsed = stored === null ? Number.NaN : Number(stored);
    if (Number.isFinite(parsed)) setChosenWidth(parsed);
  }, []);

  const persist = useCallback((width: number | null) => {
    try {
      if (width === null) window.localStorage.removeItem(NAME_COLUMN_STORAGE_KEY);
      else window.localStorage.setItem(NAME_COLUMN_STORAGE_KEY, String(width));
    } catch (error) {
      // The width still applies until the page is left; only remembering it fails.
      console.warn("[RoadmapTimeline] could not store the name column width", error);
    }
  }, []);

  const preview = useCallback((width: number) => setChosenWidth(width), []);
  const commit = useCallback(
    (width: number) => {
      setChosenWidth(width);
      persist(width);
    },
    [persist],
  );
  const reset = useCallback(() => {
    setChosenWidth(null);
    persist(null);
  }, [persist]);

  const width = clampWidth(chosenWidth ?? defaultWidth, NAME_COLUMN_MIN_WIDTH_PX, maxWidth);
  return { width, preview, commit, reset };
}

interface NameColumnResizerProps {
  width: number;
  minWidth: number;
  maxWidth: number;
  label: string;
  /** Called on every pointer move while dragging. */
  onResize: (width: number) => void;
  /** Called with the final width when a drag or key press ends. */
  onCommit: (width: number) => void;
  /** Double-click returns the column to its default width. */
  onReset: () => void;
}

/**
 * Drag handle on the divider between the name column and the timeline. It sits
 * outside the scrolling area, over the sticky column's right border, so it
 * stays on the divider at every scroll position and spans header and body.
 * The arrow keys resize it as well.
 */
function NameColumnResizer({
  width,
  minWidth,
  maxWidth,
  label,
  onResize,
  onCommit,
  onReset,
}: NameColumnResizerProps) {
  const dragRef = useRef<{ pointerId: number; startX: number; startWidth: number } | null>(null);
  const [dragging, setDragging] = useState(false);

  const widthAt = (clientX: number) => {
    const drag = dragRef.current;
    if (!drag) return width;
    return clampWidth(drag.startWidth + clientX - drag.startX, minWidth, maxWidth);
  };

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = { pointerId: event.pointerId, startX: event.clientX, startWidth: width };
    setDragging(true);
  };
  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    onResize(widthAt(event.clientX));
  };
  const endDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (dragRef.current?.pointerId !== event.pointerId) return;
    onCommit(widthAt(event.clientX));
    dragRef.current = null;
    setDragging(false);
  };
  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    const step =
      event.key === "ArrowLeft"
        ? -NAME_COLUMN_KEY_STEP_PX
        : event.key === "ArrowRight"
          ? NAME_COLUMN_KEY_STEP_PX
          : 0;
    if (step === 0) return;
    event.preventDefault();
    onCommit(clampWidth(width + step, minWidth, maxWidth));
  };

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={label}
      aria-valuenow={width}
      aria-valuemin={minWidth}
      aria-valuemax={maxWidth}
      tabIndex={0}
      title={label}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={endDrag}
      onPointerCancel={endDrag}
      onDoubleClick={onReset}
      onKeyDown={onKeyDown}
      className="group focus-visible:outline-none absolute inset-y-px z-40 flex w-2 cursor-col-resize touch-none justify-center"
      // The column's border is its last pixel; the container's 1px border sits before it.
      style={{ left: width + 0.5 - 4 }}
    >
      <span
        className={cn(
          "h-full w-0.5 transition-colors group-hover:bg-primary/60 group-focus-visible:bg-primary",
          dragging && "bg-primary",
        )}
        aria-hidden
      />
    </div>
  );
}

/** One group per lane, each milestone placed with its preview dates while it is dragged. */
function buildGroups(
  boards: RoadmapBoardResponse[],
  range: ReturnType<typeof computeWindow>,
  preview: DragPreview | null,
): GroupModel[] {
  return boards.map((board) => ({
    board,
    // A milestone whose target is not a day has no place on the grid and is
    // left out; `milestoneDates` warns about it.
    rows: sortMilestones(board.milestones).flatMap((milestone) => {
      const dates =
        preview?.milestoneId === milestone.id ? preview.dates : milestoneDates(milestone);
      return dates ? [{ milestone, dates, placement: placeMarker(range, dates) }] : [];
    }),
  }));
}

function sameCentres(a: Map<string, number>, b: Map<string, number>): boolean {
  if (a.size !== b.size) return false;
  for (const [id, y] of a) if (b.get(id) !== y) return false;
  return true;
}

/** "OCT", with the year on January and on the first column, as "JAN '27". */
function periodLabel(start: Date, index: number, zoom: RoadmapZoom, locale: Locale): string {
  if (zoom === "year") return format(start, "yyyy", { locale });
  if (zoom === "quarter") return format(start, "QQQ yyyy", { locale });
  return format(start, index === 0 || start.getMonth() === 0 ? "MMM ''yy" : "MMM", { locale });
}

interface MilestoneRowProps {
  row: MilestoneRowModel;
  rowStyle: CSSProperties;
  indent: boolean;
  nameColumnWidth: number;
  expanded: boolean;
  onToggleExpanded: () => void;
  canDrag: boolean;
  drag: ReturnType<typeof useBarDrag>;
  dateLocale: Locale;
  onSelect: (milestone: MilestoneListResponse) => void;
  /** Reports the milestone whose marker is hovered or focused, or null when it is left. */
  onActiveChange: (milestoneId: string | null) => void;
}

function MilestoneRow({
  row,
  rowStyle,
  indent,
  nameColumnWidth,
  expanded,
  onToggleExpanded,
  canDrag,
  drag,
  dateLocale,
  onSelect,
  onActiveChange,
}: MilestoneRowProps) {
  const t = useTranslations("roadmap");
  const tMilestone = useTranslations("milestone");
  const { milestone, dates, placement } = row;
  const { startX, endX, hasSpan } = placement;

  const healthText = tMilestone(`health.${milestone.health}`);
  const healthDot = MILESTONE_HEALTH_STYLES[milestone.health].bar;
  const formatDay = (date: Date) => format(date, "MMM d, yyyy", { locale: dateLocale });
  const target = formatDay(dates.targetDate);
  const dateText = dates.startDate
    ? tMilestone("sheet.dateRange", { start: formatDay(dates.startDate), target })
    : tMilestone("sheet.targetOnly", { target });
  const counts = tMilestone("progressCount", {
    done: milestone.doneCount,
    total: milestone.totalCount,
  });
  const label = t("itemLabel", { name: milestone.name, date: target, counts, health: healthText });
  const markerLabel = canDrag ? `${label}. ${t("dragHint")}` : label;
  const isClosed = milestone.status === "CLOSED";
  const hasConflict = milestone.dependsOn.some((d) => d.conflict);
  const color = milestone.color ?? NEUTRAL_BAR_COLOR;
  const percent = milestone.totalCount > 0 ? (milestone.doneCount / milestone.totalCount) * 100 : 0;

  const markerLeft = hasSpan ? startX : startX - DIAMOND_PX / 2;
  const markerWidth = hasSpan ? Math.max(endX - startX, 6) : DIAMOND_PX;
  const markerTop = hasSpan
    ? (MILESTONE_ROW_HEIGHT_PX - BAR_HEIGHT_PX) / 2
    : (MILESTONE_ROW_HEIGHT_PX - DIAMOND_PX) / 2;
  const markerHeight = hasSpan ? BAR_HEIGHT_PX : DIAMOND_PX;
  const isDragging = drag.preview?.milestoneId === milestone.id && !drag.preview.committed;

  const details = (
    <div className="max-w-64 space-y-0.5">
      <div className="font-semibold">{milestone.name}</div>
      <div>{dateText}</div>
      <div>
        {counts} · <span className="font-medium">{healthText}</span>
        {isClosed && ` · ${tMilestone("status.CLOSED")}`}
      </div>
      {milestone.dependsOn.length > 0 && (
        <div className="pt-1">
          <div className="font-medium">{t("dependency.dependsOn")}</div>
          {milestone.dependsOn.map((dependency) => (
            <div key={dependency.milestoneId} className="flex items-center gap-1">
              <span className="truncate">{dependency.name}</span>
              {dependency.conflict && (
                <span className="text-destructive shrink-0">· {t("dependency.conflict")}</span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );

  const activate = () => onActiveChange(milestone.id);
  const deactivate = () => onActiveChange(null);

  const open = () => {
    if (drag.consumeClick()) return;
    onSelect(milestone);
  };

  return (
    <div
      data-milestone-row={milestone.id}
      className="flex"
      style={{ ...rowStyle, height: MILESTONE_ROW_HEIGHT_PX }}
    >
      <div
        className={cn(
          "border-border sticky left-0 z-20 flex shrink-0 items-center gap-1.5 border-r pr-2",
          indent ? "pl-5" : "pl-1.5",
        )}
        style={{ ...rowStyle, width: nameColumnWidth }}
      >
        <button
          type="button"
          onClick={onToggleExpanded}
          aria-expanded={expanded}
          aria-label={t(expanded ? "tickets.collapse" : "tickets.expand", { name: milestone.name })}
          className="text-muted-foreground hover:text-foreground hover:bg-accent focus-visible:ring-ring flex h-6 w-6 shrink-0 items-center justify-center rounded focus-visible:ring-2 focus-visible:outline-none"
        >
          <ChevronRight
            className={cn("h-4 w-4 transition-transform", expanded && "rotate-90")}
            aria-hidden
          />
        </button>
        <span
          className="h-3 w-3 shrink-0 rounded-sm"
          style={{ backgroundColor: color }}
          aria-hidden
        />
        <button
          type="button"
          onClick={() => onSelect(milestone)}
          aria-label={label}
          title={milestone.name}
          className={cn(
            "text-foreground focus-visible:ring-ring min-w-0 flex-1 truncate rounded-sm text-left text-sm hover:underline focus-visible:ring-2 focus-visible:outline-none",
            isClosed && "text-muted-foreground line-through",
          )}
        >
          {milestone.name}
        </button>
        {hasConflict && (
          <AlertTriangle
            className="text-destructive h-3.5 w-3.5 shrink-0"
            aria-label={t("dependency.conflict")}
          />
        )}
        <span
          className={cn("h-2 w-2 shrink-0 rounded-full", healthDot)}
          title={healthText}
          aria-hidden
        />
      </div>

      <div className="relative flex-1" {...{ [ROADMAP_GRID_ATTRIBUTE]: "" }}>
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              aria-label={markerLabel}
              onClick={open}
              onPointerEnter={activate}
              onPointerLeave={deactivate}
              onFocus={activate}
              onBlur={deactivate}
              onKeyDown={canDrag ? drag.keyHandler(milestone, dates) : undefined}
              {...(canDrag ? drag.pointerHandlers(milestone, dates, "move") : {})}
              className={cn(
                "group focus-visible:ring-ring absolute z-10 rounded-sm focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:outline-none",
                canDrag ? "cursor-grab touch-none active:cursor-grabbing" : "cursor-pointer",
                isDragging && "cursor-grabbing",
                isClosed && "opacity-60",
              )}
              style={{ left: markerLeft, width: markerWidth, top: markerTop, height: markerHeight }}
            >
              {hasSpan ? (
                <span
                  className={cn(
                    "absolute inset-0 overflow-hidden rounded-[3px] shadow-sm transition-shadow group-hover:shadow-md",
                    isDragging && "shadow-md",
                  )}
                  style={{ backgroundColor: color }}
                  aria-hidden
                >
                  <span
                    className="absolute inset-y-0 left-0 bg-black/15"
                    style={{ width: `${percent}%` }}
                  />
                </span>
              ) : (
                <span
                  className="absolute inset-[2px] rotate-45 rounded-[2px] shadow-sm"
                  style={{ backgroundColor: color }}
                  aria-hidden
                />
              )}
              {canDrag && hasSpan && (
                <>
                  <span
                    className="absolute inset-y-0 left-0 w-2 cursor-ew-resize rounded-l-[3px] hover:bg-black/20"
                    {...drag.pointerHandlers(milestone, dates, "start")}
                    aria-hidden
                  />
                  <span
                    className="absolute inset-y-0 right-0 w-2 cursor-ew-resize rounded-r-[3px] hover:bg-black/20"
                    {...drag.pointerHandlers(milestone, dates, "end")}
                    aria-hidden
                  />
                </>
              )}
            </button>
          </TooltipTrigger>
          <TooltipContent side="top">{details}</TooltipContent>
        </Tooltip>
      </div>
    </div>
  );
}
