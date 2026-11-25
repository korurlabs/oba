"use client";

import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { clampPercentage, SCORE_LEVEL_STROKE_CLASSES } from "../lib/score-level";
import type { Cyberscore } from "../schemas";
import { CyberscoreLevelChip } from "./cyberscore-level-chip";

const CENTER_X = 64;
const CENTER_Y = 64;
const RADIUS = 52;
/** A half circle from the left end to the right end, with room for the stroke. */
const ARC_PATH = `M ${CENTER_X - RADIUS} ${CENTER_Y} A ${RADIUS} ${RADIUS} 0 0 1 ${CENTER_X + RADIUS} ${CENTER_Y}`;
/** Survey ticks inside the arc at the level bounds, so the arc reads as a scale. */
const TICKS = [0, 20, 40, 60, 80, 100] as const;
const TICK_OUTER = RADIUS - 8;
const TICK_INNER = RADIUS - 13;

/** The point at `radius` from the centre for a percentage along the half circle. */
function pointAt(percentage: number, radius: number) {
  const angle = Math.PI * (1 - percentage / 100);
  return { x: CENTER_X + radius * Math.cos(angle), y: CENTER_Y - radius * Math.sin(angle) };
}

interface CyberscoreGaugeProps {
  score: Cyberscore;
}

/**
 * The current score as a half-circle gauge filled to its percentage in the
 * ink of its level, on a square-ended track with ticks at the level bounds.
 * The percentage and the level name are printed with the arc, so the colour
 * only ranks the level and is never its only sign; the SVG itself carries one
 * spoken label for screen readers.
 */
export function CyberscoreGauge({ score }: CyberscoreGaugeProps) {
  const t = useTranslations("cyberscore");
  const { formatDateTime } = useDateFormat();
  const percentage = clampPercentage(score.scorePercentage);
  const levelLabel = t(`levels.${score.scoreLevel}`);

  return (
    <div className="flex flex-col items-center gap-3">
      <svg
        role="img"
        aria-label={t("gauge.ariaLabel", { score: percentage, level: levelLabel })}
        viewBox="0 0 128 72"
        className="w-full max-w-64"
      >
        <path
          d={ARC_PATH}
          fill="none"
          className="stroke-primary/10 dark:stroke-korur-slate"
          strokeWidth={10}
        />
        {percentage > 0 && (
          <path
            d={ARC_PATH}
            fill="none"
            pathLength={100}
            strokeDasharray={`${percentage} 100`}
            className={SCORE_LEVEL_STROKE_CLASSES[score.scoreLevel]}
            strokeWidth={10}
          />
        )}
        {TICKS.map((tick) => {
          const outer = pointAt(tick, TICK_OUTER);
          const inner = pointAt(tick, TICK_INNER);
          return (
            <line
              key={tick}
              x1={outer.x}
              y1={outer.y}
              x2={inner.x}
              y2={inner.y}
              className="stroke-(--kazan-grid-mark)"
              strokeWidth={1}
            />
          );
        })}
        <text
          x={CENTER_X}
          y={58}
          textAnchor="middle"
          aria-hidden
          className="fill-foreground font-mono text-[20px] font-semibold tabular-nums"
        >
          {percentage}%
        </text>
      </svg>

      <CyberscoreLevelChip level={score.scoreLevel} className="px-2 py-1 text-xs" />

      <div className="text-muted-foreground flex flex-col items-center gap-1 text-center font-mono text-[11px] tracking-[0.08em] uppercase">
        <p>{t("gauge.scorableChecks", { count: score.scorableCheckCount })}</p>
        <p>
          {score.lastCalculatedAt
            ? t("gauge.lastCalculated", { date: formatDateTime(score.lastCalculatedAt) })
            : t("gauge.notCalculated")}
        </p>
      </div>
    </div>
  );
}
