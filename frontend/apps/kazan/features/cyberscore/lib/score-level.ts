import { CyberscoreLevel } from "../schemas";

/*
 * One risk scale for every place a level shows: critical and high in red,
 * medium in amber, low and minimal in green, and not-yet-scored in the muted
 * ink so it reads as neither good nor bad. The level name is always shown
 * next to the colour, which only ranks it.
 */

/** Outline chip inks per level, for a `Badge variant="outline"`. */
export const SCORE_LEVEL_BADGE_CLASSES: Record<CyberscoreLevel, string> = {
  [CyberscoreLevel.UNKNOWN]: "border-border text-muted-foreground",
  [CyberscoreLevel.CRITICAL]: "border-red-600/50 text-red-700 dark:text-red-400",
  [CyberscoreLevel.HIGH]: "border-red-600/40 text-red-700 dark:text-red-400",
  [CyberscoreLevel.MEDIUM]: "border-amber-600/50 text-amber-700 dark:text-amber-400",
  [CyberscoreLevel.LOW]: "border-green-600/40 text-green-700 dark:text-green-400",
  [CyberscoreLevel.MINIMAL]: "border-green-600/50 text-green-700 dark:text-green-400",
};

/** Text ink per level, for a percentage or a level name. */
export const SCORE_LEVEL_TEXT_CLASSES: Record<CyberscoreLevel, string> = {
  [CyberscoreLevel.UNKNOWN]: "text-muted-foreground",
  [CyberscoreLevel.CRITICAL]: "text-red-700 dark:text-red-400",
  [CyberscoreLevel.HIGH]: "text-red-700 dark:text-red-400",
  [CyberscoreLevel.MEDIUM]: "text-amber-700 dark:text-amber-400",
  [CyberscoreLevel.LOW]: "text-green-700 dark:text-green-400",
  [CyberscoreLevel.MINIMAL]: "text-green-700 dark:text-green-400",
};

/** Stroke ink per level, for the gauge arc and chart lines drawn in SVG. */
export const SCORE_LEVEL_STROKE_CLASSES: Record<CyberscoreLevel, string> = {
  [CyberscoreLevel.UNKNOWN]: "stroke-muted-foreground",
  [CyberscoreLevel.CRITICAL]: "stroke-red-600 dark:stroke-red-400",
  [CyberscoreLevel.HIGH]: "stroke-red-500 dark:stroke-red-400",
  [CyberscoreLevel.MEDIUM]: "stroke-amber-500 dark:stroke-amber-400",
  [CyberscoreLevel.LOW]: "stroke-green-500 dark:stroke-green-400",
  [CyberscoreLevel.MINIMAL]: "stroke-green-600 dark:stroke-green-400",
};

/** Fill ink per level, for bars and dots. */
export const SCORE_LEVEL_FILL_CLASSES: Record<CyberscoreLevel, string> = {
  [CyberscoreLevel.UNKNOWN]: "bg-muted-foreground/40",
  [CyberscoreLevel.CRITICAL]: "bg-red-600 dark:bg-red-400",
  [CyberscoreLevel.HIGH]: "bg-red-500 dark:bg-red-400",
  [CyberscoreLevel.MEDIUM]: "bg-amber-500 dark:bg-amber-400",
  [CyberscoreLevel.LOW]: "bg-green-500 dark:bg-green-400",
  [CyberscoreLevel.MINIMAL]: "bg-green-600 dark:bg-green-400",
};

/** Clamps and rounds a raw percentage into a whole number from 0 to 100. */
export function clampPercentage(percentage: number): number {
  if (!Number.isFinite(percentage)) return 0;
  return Math.max(0, Math.min(100, Math.round(percentage)));
}

/**
 * The level of a percentage, with the server's bounds: up to 20 critical, 40
 * high, 60 medium, 80 low, above that minimal. The overall score comes with
 * its level, but a category of the breakdown has only its percentage, so the
 * breakdown derives its level here and a category and the overall score at
 * the same percentage share label and colour. It never returns `UNKNOWN`: a
 * category without scorable checks has no percentage at all.
 */
export function levelFromPercentage(percentage: number): CyberscoreLevel {
  const value = clampPercentage(percentage);
  if (value <= 20) return CyberscoreLevel.CRITICAL;
  if (value <= 40) return CyberscoreLevel.HIGH;
  if (value <= 60) return CyberscoreLevel.MEDIUM;
  if (value <= 80) return CyberscoreLevel.LOW;
  return CyberscoreLevel.MINIMAL;
}
