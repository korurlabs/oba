"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { SCORE_LEVEL_BADGE_CLASSES } from "../lib/score-level";
import type { CyberscoreLevel } from "../schemas";

interface CyberscoreLevelChipProps {
  level: CyberscoreLevel;
  className?: string;
}

/**
 * A risk level as Kazan's square outline chip, inked in the level's colour.
 * The chip always spells the level name, so the colour only ranks it.
 */
export function CyberscoreLevelChip({ level, className }: CyberscoreLevelChipProps) {
  const t = useTranslations("cyberscore");
  return (
    <span
      className={cn("kazan-grid-chip font-semibold", SCORE_LEVEL_BADGE_CLASSES[level], className)}
    >
      {t(`levels.${level}`)}
    </span>
  );
}
