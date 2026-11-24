"use client";

import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { format, parseISO } from "date-fns";
import { useLocale, useTranslations } from "next-intl";
import { InfoTip } from "@/components/ui/info-tip";
import { getDateFnsLocale } from "@oba/shared/lib/date-fns-locale";
import type { HomeBurndownPointResponse } from "../schemas";

const HEIGHT = 176;
const MARGIN = { top: 8, right: 12, bottom: 24, left: 32 } as const;
/** Width used before the container is measured, and on the server. */
const FALLBACK_WIDTH = 480;
/** An axis date closer than this to another one is left out, so labels never overlap. */
const MIN_LABEL_GAP = 56;

interface BurndownChartProps {
  sprintName: string;
  /** One point per sprint day, as the server sends them: dates in the updates channel's zone. */
  points: HomeBurndownPointResponse[];
  totalTickets: number;
  openTickets: number;
  /** Today in the same zone as `points`, `YYYY-MM-DD`; it places the today mark. */
  today: string;
}

/**
 * The sprint's burndown as an inline SVG: the tickets still open at the end
 * of each day up to today in the brand blue, and the even pace from the total
 * down to zero as a muted dashed line. The dash, not only the colour, tells
 * the two lines apart. The InfoTip names both lines; there is no legend, by
 * the page's rule that explanations live only in the tip.
 *
 * Hover or the arrow keys move a crosshair that snaps to a day and shows its
 * values, and a visually hidden table gives screen readers every day.
 */
export function BurndownChart({ sprintName, points, totalTickets, openTickets, today }: BurndownChartProps) {
  const t = useTranslations("home.burndown");
  const locale = useLocale();
  const dateLocale = getDateFnsLocale(locale);
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
    const maxValue = Math.max(
      1,
      totalTickets,
      ...points.map((point) => Math.max(point.remaining ?? 0, point.ideal)),
    );
    const step = points.length > 1 ? innerWidth / (points.length - 1) : 0;
    const x = (index: number) => MARGIN.left + (points.length > 1 ? index * step : innerWidth / 2);
    const y = (value: number) => MARGIN.top + innerHeight * (1 - value / maxValue);

    const actual = points
      .map((point, index) => (point.remaining === null ? null : `${x(index)},${y(point.remaining)}`))
      .filter((value): value is string => value !== null);
    const ideal = points.map((point, index) => `${x(index)},${y(point.ideal)}`);
    const lastActualIndex = points.reduce(
      (last, point, index) => (point.remaining === null ? last : index),
      -1,
    );
    const todayIndex = points.findIndex((point) => point.date === today);

    return { innerWidth, innerHeight, maxValue, x, y, actual, ideal, lastActualIndex, todayIndex };
  }, [points, totalTickets, today, width]);

  const formatDay = (day: string) => format(parseISO(day), "MMM d", { locale: dateLocale });

  // Start, today and end; a label too close to one already placed is dropped.
  const axisIndexes = useMemo(() => {
    const candidates = [0, points.length - 1, geometry.todayIndex].filter(
      (index, position, all) => index >= 0 && all.indexOf(index) === position,
    );
    const placed: number[] = [];
    for (const index of candidates) {
      if (placed.every((other) => Math.abs(geometry.x(index) - geometry.x(other)) >= MIN_LABEL_GAP)) {
        placed.push(index);
      }
    }
    return placed;
  }, [geometry, points.length]);

  const indexAt = (clientX: number, rect: DOMRect) => {
    if (points.length <= 1) return 0;
    const step = geometry.innerWidth / (points.length - 1);
    const index = Math.round((clientX - rect.left - MARGIN.left) / step);
    return Math.min(points.length - 1, Math.max(0, index));
  };

  const handlePointerMove = (event: PointerEvent<SVGSVGElement>) => {
    setActiveIndex(indexAt(event.clientX, event.currentTarget.getBoundingClientRect()));
  };

  const handleKeyDown = (event: KeyboardEvent<SVGSVGElement>) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const start = activeIndex ?? Math.max(geometry.lastActualIndex, 0);
    const next = event.key === "ArrowLeft" ? start - 1 : start + 1;
    setActiveIndex(Math.min(points.length - 1, Math.max(0, next)));
  };

  const active = activeIndex === null ? undefined : points[activeIndex];
  const lastActual = geometry.lastActualIndex >= 0 ? points[geometry.lastActualIndex] : undefined;
  const chartLabel = t("chartLabel", { name: sprintName, remaining: openTickets, total: totalTickets });

  return (
    <div className="flex min-w-0 flex-col gap-2">
      <div className="flex items-center gap-2">
        <p className="text-foreground text-sm font-medium">
          {t("summary", { remaining: openTickets, total: totalTickets })}
        </p>
        <InfoTip label={t("infoLabel")}>{t("info")}</InfoTip>
      </div>

      <div ref={containerRef} className="relative w-full">
        <svg
          role="img"
          aria-label={chartLabel}
          tabIndex={0}
          width={width}
          height={HEIGHT}
          className="focus-visible:ring-ring block rounded-md outline-none focus-visible:ring-2"
          onPointerMove={handlePointerMove}
          onPointerLeave={() => setActiveIndex(null)}
          onBlur={() => setActiveIndex(null)}
          onKeyDown={handleKeyDown}
        >
          {/* Hairline grid at zero and at the top value; the axis stays recessive. */}
          {[0, geometry.maxValue].map((value) => (
            <g key={value}>
              <line
                x1={MARGIN.left}
                x2={width - MARGIN.right}
                y1={geometry.y(value)}
                y2={geometry.y(value)}
                className="stroke-border"
                strokeWidth={1}
              />
              <text
                x={MARGIN.left - 6}
                y={geometry.y(value)}
                textAnchor="end"
                dominantBaseline="middle"
                className="fill-muted-foreground text-[10px] tabular-nums"
              >
                {value}
              </text>
            </g>
          ))}

          {axisIndexes.map((index) => (
            <text
              key={index}
              x={geometry.x(index)}
              y={HEIGHT - 6}
              textAnchor={index === 0 ? "start" : index === points.length - 1 ? "end" : "middle"}
              className="fill-muted-foreground text-[10px]"
            >
              {index === geometry.todayIndex ? t("today") : formatDay(points[index]!.date)}
            </text>
          ))}

          {geometry.todayIndex >= 0 && (
            <line
              x1={geometry.x(geometry.todayIndex)}
              x2={geometry.x(geometry.todayIndex)}
              y1={MARGIN.top}
              y2={MARGIN.top + geometry.innerHeight}
              className="stroke-muted-foreground/40"
              strokeWidth={1}
            />
          )}

          <polyline
            points={geometry.ideal.join(" ")}
            fill="none"
            className="stroke-muted-foreground"
            strokeWidth={2}
            strokeDasharray="4 4"
            strokeLinecap="round"
          />

          {geometry.actual.length > 1 && (
            <polyline
              points={geometry.actual.join(" ")}
              fill="none"
              className="stroke-korur-blue-hover dark:stroke-korur-blue"
              strokeWidth={2}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          )}

          {lastActual && lastActual.remaining !== null && (
            <circle
              cx={geometry.x(geometry.lastActualIndex)}
              cy={geometry.y(lastActual.remaining)}
              r={4}
              strokeWidth={2}
              className="fill-korur-blue-hover stroke-card dark:fill-korur-blue"
            />
          )}

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
              left: Math.min(Math.max(geometry.x(activeIndex) - 64, 0), Math.max(width - 128, 0)),
              width: 128,
            }}
          >
            <p className="text-foreground font-medium">{formatDay(active.date)}</p>
            <p className="flex items-center gap-1.5">
              <span className="bg-korur-blue-hover dark:bg-korur-blue h-0.5 w-3" />
              <span className="text-muted-foreground">{t("remaining")}</span>
              <span className="text-foreground ml-auto tabular-nums">{active.remaining ?? "-"}</span>
            </p>
            <p className="flex items-center gap-1.5">
              <span className="border-muted-foreground w-3 border-t-2 border-dashed" />
              <span className="text-muted-foreground">{t("ideal")}</span>
              <span className="text-foreground ml-auto tabular-nums">{active.ideal}</span>
            </p>
          </div>
        )}
      </div>

      <table className="sr-only">
        <caption>{chartLabel}</caption>
        <thead>
          <tr>
            <th scope="col">{t("date")}</th>
            <th scope="col">{t("remaining")}</th>
            <th scope="col">{t("ideal")}</th>
          </tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={point.date}>
              <th scope="row">{formatDay(point.date)}</th>
              <td>{point.remaining ?? ""}</td>
              <td>{point.ideal}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
