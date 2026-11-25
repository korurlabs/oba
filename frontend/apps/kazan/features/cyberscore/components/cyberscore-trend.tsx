"use client";

import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { useLocale, useTranslations } from "next-intl";
import { formatDateOnly } from "@oba/shared/lib/date-only";
import { cn } from "@/lib/utils";
import { clampPercentage, SCORE_LEVEL_FILL_CLASSES } from "../lib/score-level";
import type { CyberscoreTrendPoint } from "../schemas";

const HEIGHT = 176;
const MARGIN = { top: 8, right: 12, bottom: 24, left: 36 } as const;
/** Width used before the container is measured, and on the server. */
const FALLBACK_WIDTH = 480;
/** Hairlines of the percentage axis; the score is always between 0 and 100. */
const Y_TICKS = [0, 50, 100] as const;
const TOOLTIP_WIDTH = 140;

interface CyberscoreTrendProps {
  /** One point per business day, oldest first, dates as `YYYY-MM-DD`. */
  points: CyberscoreTrendPoint[];
}

/**
 * The score over the trend window as an inline SVG line on a fixed 0 to 100
 * axis, so a small change does not look like a large one. The axis is drawn
 * as the survey grid: hairlines in the grid's blue, the middle one dashed,
 * and mono figures. Hover or the arrow
 * keys move a crosshair that snaps to a day and shows its score and level,
 * and a visually hidden table gives screen readers every day.
 *
 * Days are calendar days that the server already fixed in its business zone,
 * so they are formatted as date-only values and never shift with the
 * viewer's zone. With fewer than two days there is no line to draw.
 */
export function CyberscoreTrend({ points }: CyberscoreTrendProps) {
  const t = useTranslations("cyberscore");
  if (points.length < 2) {
    return <p className="text-muted-foreground py-6 text-center text-sm">{t("trend.empty")}</p>;
  }
  return <TrendChart points={points} />;
}

/** The chart itself, mounted only with a line to draw, so it measures its container from the start. */
function TrendChart({ points }: CyberscoreTrendProps) {
  const t = useTranslations("cyberscore");
  const locale = useLocale();
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(FALLBACK_WIDTH);
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry) setWidth(Math.max(entry.contentRect.width, 160));
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const geometry = useMemo(() => {
    const innerWidth = width - MARGIN.left - MARGIN.right;
    const innerHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
    const step = points.length > 1 ? innerWidth / (points.length - 1) : 0;
    const x = (index: number) => MARGIN.left + index * step;
    const y = (value: number) => MARGIN.top + innerHeight * (1 - clampPercentage(value) / 100);
    const line = points.map((point, index) => `${x(index)},${y(point.scorePercentage)}`).join(" ");
    return { innerWidth, innerHeight, step, x, y, line };
  }, [points, width]);

  const formatDay = (day: string) => formatDateOnly(day, locale);
  const lastIndex = points.length - 1;
  const last = points[lastIndex]!;

  const indexAt = (clientX: number, rect: DOMRect) => {
    const index = Math.round((clientX - rect.left - MARGIN.left) / geometry.step);
    return Math.min(lastIndex, Math.max(0, index));
  };

  const handlePointerMove = (event: PointerEvent<SVGSVGElement>) => {
    setActiveIndex(indexAt(event.clientX, event.currentTarget.getBoundingClientRect()));
  };

  const handleKeyDown = (event: KeyboardEvent<SVGSVGElement>) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const start = activeIndex ?? lastIndex;
    const next = event.key === "ArrowLeft" ? start - 1 : start + 1;
    setActiveIndex(Math.min(lastIndex, Math.max(0, next)));
  };

  const active = activeIndex === null ? undefined : points[activeIndex];

  return (
    <div className="flex min-w-0 flex-col gap-2">
      <div ref={containerRef} className="relative w-full">
        <svg
          role="img"
          aria-label={t("trend.seriesLabel")}
          tabIndex={0}
          width={width}
          height={HEIGHT}
          className="focus-visible:ring-ring block outline-none focus-visible:ring-2"
          onPointerMove={handlePointerMove}
          onPointerLeave={() => setActiveIndex(null)}
          onBlur={() => setActiveIndex(null)}
          onKeyDown={handleKeyDown}
        >
          {Y_TICKS.map((value) => (
            <g key={value}>
              <line
                x1={MARGIN.left}
                x2={width - MARGIN.right}
                y1={geometry.y(value)}
                y2={geometry.y(value)}
                className="stroke-primary/25"
                strokeWidth={1}
                strokeDasharray={value === 0 || value === 100 ? undefined : "4 4"}
              />
              <text
                x={MARGIN.left - 6}
                y={geometry.y(value)}
                textAnchor="end"
                dominantBaseline="middle"
                className="fill-muted-foreground font-mono text-[10px] tabular-nums"
              >
                {value}%
              </text>
            </g>
          ))}

          {[0, lastIndex].map((index) => (
            <text
              key={index}
              x={geometry.x(index)}
              y={HEIGHT - 6}
              textAnchor={index === 0 ? "start" : "end"}
              className="fill-muted-foreground font-mono text-[10px] tracking-[0.08em] uppercase"
            >
              {formatDay(points[index]!.date)}
            </text>
          ))}

          <polyline
            points={geometry.line}
            fill="none"
            className="stroke-korur-blue-hover dark:stroke-korur-blue"
            strokeWidth={2}
            strokeLinejoin="round"
            strokeLinecap="round"
          />

          <circle
            cx={geometry.x(lastIndex)}
            cy={geometry.y(last.scorePercentage)}
            r={4}
            strokeWidth={2}
            className="fill-korur-blue-hover stroke-card dark:fill-korur-blue"
          />

          {active && activeIndex !== null && (
            <line
              x1={geometry.x(activeIndex)}
              x2={geometry.x(activeIndex)}
              y1={MARGIN.top}
              y2={MARGIN.top + geometry.innerHeight}
              className="stroke-foreground/30"
              strokeWidth={1}
            />
          )}
        </svg>

        {active && activeIndex !== null && (
          <div
            aria-hidden
            className="bg-popover text-popover-foreground border-border pointer-events-none absolute top-0 z-10 border px-2 py-1.5 text-xs shadow-(--kazan-grid-shadow)"
            style={{
              left: Math.min(
                Math.max(geometry.x(activeIndex) - TOOLTIP_WIDTH / 2, 0),
                Math.max(width - TOOLTIP_WIDTH, 0)
              ),
              width: TOOLTIP_WIDTH,
            }}
          >
            <p className="text-foreground font-mono text-[11px] font-semibold tracking-[0.08em] uppercase">
              {formatDay(active.date)}
            </p>
            <p className="flex items-center gap-1.5">
              <span
                className={cn("size-2 shrink-0", SCORE_LEVEL_FILL_CLASSES[active.scoreLevel])}
              />
              <span className="text-muted-foreground truncate">
                {t(`levels.${active.scoreLevel}`)}
              </span>
              <span className="text-foreground ml-auto font-mono tabular-nums">
                {clampPercentage(active.scorePercentage)}%
              </span>
            </p>
          </div>
        )}
      </div>

      <table className="sr-only">
        <caption>{t("trend.tableCaption")}</caption>
        <thead>
          <tr>
            <th scope="col">{t("trend.date")}</th>
            <th scope="col">{t("trend.score")}</th>
            <th scope="col">{t("trend.level")}</th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.date}>
              <th scope="row">{formatDay(point.date)}</th>
              <td>{clampPercentage(point.scorePercentage)}%</td>
              <td>{t(`levels.${point.scoreLevel}`)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
