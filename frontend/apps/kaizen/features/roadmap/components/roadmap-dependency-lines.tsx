"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Tooltip, TooltipContent, TooltipTrigger } from "@oba/ui/components/tooltip";
import { buildConnectorPath } from "../lib/connector-path";

export type DependencyLine = {
  key: string;
  /** The milestone depended on. */
  fromId: string;
  fromName: string;
  /** The milestone that depends on it. */
  toId: string;
  toName: string;
  /** Right end of the dependency's marker. */
  fromX: number;
  fromY: number;
  /** Left end of the dependent's marker. */
  toX: number;
  toY: number;
  conflict: boolean;
};

interface RoadmapDependencyLinesProps {
  lines: DependencyLine[];
  width: number;
  height: number;
  /** Height of a milestone row; backward connectors run along its edges. */
  rowHeight: number;
  /** A milestone whose marker is hovered or focused; its connectors are highlighted. */
  activeMilestoneId: string | null;
}

/** Hit area around a connector, wider than the stroke so a thin line is easy to hover. */
const HIT_STROKE_PX = 10;

/**
 * Connectors from each dependency's end to its dependent's start, drawn over
 * the grid lines and under the markers. A connector whose dependent starts
 * before the dependency is due is a conflict and drawn in the destructive
 * colour. Hovering a connector, or either marker it joins, highlights it.
 */
export function RoadmapDependencyLines({
  lines,
  width,
  height,
  rowHeight,
  activeMilestoneId,
}: RoadmapDependencyLinesProps) {
  const t = useTranslations("roadmap.dependency");
  const [hoveredKey, setHoveredKey] = useState<string | null>(null);

  const shapes = useMemo(
    () =>
      lines.map((line) => ({
        line,
        geometry: buildConnectorPath({
          fromX: line.fromX,
          fromY: line.fromY,
          toX: line.toX,
          toY: line.toY,
          rowHeight,
        }),
      })),
    [lines, rowHeight],
  );

  if (shapes.length === 0 || height === 0) return null;

  const isActive = (line: DependencyLine) =>
    line.key === hoveredKey ||
    (activeMilestoneId !== null &&
      (line.fromId === activeMilestoneId || line.toId === activeMilestoneId));
  // Highlighted connectors are drawn last so they sit on top of the others.
  const ordered = [...shapes].sort(
    (a, b) => Number(isActive(a.line)) - Number(isActive(b.line)),
  );

  return (
    <svg
      className="pointer-events-none absolute top-0 z-[5] overflow-visible"
      width={width}
      height={height}
      aria-hidden
    >
      {ordered.map(({ line, geometry }) => {
        const active = isActive(line);
        const color = line.conflict ? "var(--destructive)" : "var(--muted-foreground)";
        const opacity = active ? 1 : line.conflict ? 0.85 : 0.6;
        return (
          <g key={line.key} opacity={opacity}>
            <path
              d={geometry.path}
              fill="none"
              stroke={color}
              strokeWidth={active ? 2.25 : 1.5}
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden
            />
            <path d={geometry.arrow} fill={color} stroke={color} strokeLinejoin="round" aria-hidden />
            <Tooltip>
              <TooltipTrigger asChild>
                <path
                  d={geometry.path}
                  fill="none"
                  stroke="transparent"
                  strokeWidth={HIT_STROKE_PX}
                  style={{ pointerEvents: "stroke" }}
                  onPointerEnter={() => setHoveredKey(line.key)}
                  onPointerLeave={() =>
                    setHoveredKey((current) => (current === line.key ? null : current))
                  }
                />
              </TooltipTrigger>
              <TooltipContent side="top">
                <div className="max-w-64 space-y-0.5">
                  <div className="font-medium">
                    {t("connector", { dependent: line.toName, dependency: line.fromName })}
                  </div>
                  {line.conflict && <div className="text-destructive">{t("conflict")}</div>}
                </div>
              </TooltipContent>
            </Tooltip>
          </g>
        );
      })}
    </svg>
  );
}
