"use client";

import { useTranslations } from "next-intl";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { cn } from "@/lib/utils";
import { clampPercentage, levelFromPercentage, SCORE_LEVEL_FILL_CLASSES } from "../lib/score-level";
import { CHECK_CATEGORIES, type CheckCategory, type Cyberscore } from "../schemas";
import { CyberscoreLevelChip } from "./cyberscore-level-chip";

/** The check list narrowed to one category, which a breakdown row opens. */
export function categoryChecksHref(category: CheckCategory): string {
  return `/cyberscore/checks?category=${category}`;
}

interface CyberscoreBreakdownProps {
  breakdown: Cyberscore["breakdownByCategory"];
}

/**
 * The score of each category that has at least one scorable check, in the
 * categories' display order. The rows come from the category enum, so a
 * category added to or removed from it needs no change here. Each row prints
 * its percentage and level name next to the bar, so the bar's colour is
 * never the only sign of the level. The bars are the square meters of the
 * home sprint strip.
 *
 * Each row opens the check list narrowed to its category, so a weak score
 * leads straight to the checks behind it. The rows take the ticket card's
 * surface and hover edge, the cue Kazan uses for "this opens".
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
    return <p className="text-muted-foreground py-6 text-center text-sm">{t("breakdown.empty")}</p>;
  }

  return (
    <ul className="flex flex-col gap-1.5 text-sm">
      {rows.map(({ category, percentage, level }) => {
        const categoryLabel = t(`categories.${category}`);
        return (
          <li key={category}>
            <Link
              href={categoryChecksHref(category)}
              aria-label={t("breakdown.rowLink", {
                category: categoryLabel,
                score: percentage,
                level: t(`levels.${level}`),
              })}
              className={cn(
                "kazan-card border-border grid grid-cols-[minmax(0,1fr)_40%_8rem] items-center gap-3 rounded-none border px-3 py-2",
                "transition-[border-color,box-shadow] duration-150 ease-in-out",
                "hover:border-primary/50 focus-visible:ring-ring focus-visible:border-primary/50 outline-none focus-visible:ring-2"
              )}
            >
              <span className="text-foreground min-w-0 font-medium">{categoryLabel}</span>
              <span aria-hidden className="flex items-center gap-2">
                <span className="bg-primary/10 dark:bg-korur-slate h-2 flex-1 overflow-hidden">
                  <span
                    className={cn("block h-full", SCORE_LEVEL_FILL_CLASSES[level])}
                    style={{ width: `${percentage}%` }}
                  />
                </span>
                <span className="text-foreground w-10 shrink-0 text-right font-mono text-xs tabular-nums">
                  {percentage}%
                </span>
              </span>
              <CyberscoreLevelChip level={level} className="justify-self-end" />
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
