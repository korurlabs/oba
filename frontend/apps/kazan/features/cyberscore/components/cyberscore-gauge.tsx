"use client";

import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  clampPercentage,
  SCORE_LEVEL_BADGE_CLASSES,
  SCORE_LEVEL_STROKE_CLASSES,
} from "../lib/score-level";
import type { Cyberscore } from "../schemas";

/** A half circle from the left end to the right end, with room for the stroke. */
const ARC_PATH = "M 12 64 A 52 52 0 0 1 116 64";

interface CyberscoreGaugeProps {
  score: Cyberscore;
}

/**
 * The current score as a half-circle gauge filled to its percentage in the
 * ink of its level. The percentage and the level name are printed beside the
 * arc, so the colour only ranks the level and is never its only sign; the
 * SVG itself carries one spoken label for screen readers.
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
          className="stroke-muted"
          strokeWidth={10}
          strokeLinecap="round"
        />
        {percentage > 0 && (
          <path
            d={ARC_PATH}
            fill="none"
            pathLength={100}
            strokeDasharray={`${percentage} 100`}
            className={SCORE_LEVEL_STROKE_CLASSES[score.scoreLevel]}
            strokeWidth={10}
            strokeLinecap="round"
          />
        )}
        <text
          x={64}
          y={58}
          textAnchor="middle"
          aria-hidden
          className="fill-foreground text-[22px] font-semibold tabular-nums"
        >
          {percentage}%
        </text>
      </svg>

      <Badge
        variant="outline"
        className={cn("text-sm", SCORE_LEVEL_BADGE_CLASSES[score.scoreLevel])}
      >
        {levelLabel}
      </Badge>

      <div className="text-muted-foreground flex flex-col items-center gap-0.5 text-xs">
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
