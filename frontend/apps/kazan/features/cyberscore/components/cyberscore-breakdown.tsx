"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  clampPercentage,
  levelFromPercentage,
  SCORE_LEVEL_BADGE_CLASSES,
  SCORE_LEVEL_FILL_CLASSES,
} from "../lib/score-level";
import { CHECK_CATEGORIES, type Cyberscore } from "../schemas";

interface CyberscoreBreakdownProps {
  breakdown: Cyberscore["breakdownByCategory"];
}

/**
 * The score of each category that has at least one scorable check, in the
 * categories' display order. The rows come from the category enum, so a
 * category added to or removed from it needs no change here. Each row prints
 * its percentage and level name next to the bar, so the bar's colour is
 * never the only sign of the level.
 */
export function CyberscoreBreakdown({ breakdown }: CyberscoreBreakdownProps) {
  const t = useTranslations("cyberscore");
  const rows = CHECK_CATEGORIES.flatMap((category) => {
    const raw = breakdown[category];
    if (raw === undefined) return [];
    const percentage = clampPercentage(raw);
    return [{ category, percentage, level: levelFromPercentage(percentage) }];
  });

  if (rows.length === 0) {
    return <p className="text-muted-foreground text-sm">{t("breakdown.empty")}</p>;
  }

  return (
    <table className="w-full text-sm">
      <thead className="sr-only">
        <tr>
          <th scope="col">{t("breakdown.category")}</th>
          <th scope="col">{t("breakdown.score")}</th>
          <th scope="col">{t("breakdown.level")}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(({ category, percentage, level }) => {
          const categoryLabel = t(`categories.${category}`);
          return (
            <tr key={category} className="border-border border-b border-dashed last:border-b-0">
              <th scope="row" className="text-foreground py-2.5 pr-3 text-left font-medium">
                {categoryLabel}
              </th>
              <td className="w-2/5 py-2.5 pr-3">
                <div className="flex items-center gap-2">
                  <div
                    role="meter"
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={percentage}
                    aria-label={t("breakdown.scoreAria", {
                      category: categoryLabel,
                      score: percentage,
                    })}
                    className="bg-muted h-1.5 flex-1 overflow-hidden"
                  >
                    <div
                      className={cn("h-full", SCORE_LEVEL_FILL_CLASSES[level])}
                      style={{ width: `${percentage}%` }}
                    />
                  </div>
                  <span className="text-foreground w-10 shrink-0 text-right tabular-nums">
                    {percentage}%
                  </span>
                </div>
              </td>
              <td className="py-2.5 text-right">
                <Badge
                  variant="outline"
                  className={cn("whitespace-nowrap", SCORE_LEVEL_BADGE_CLASSES[level])}
                >
                  {t(`levels.${level}`)}
                </Badge>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
