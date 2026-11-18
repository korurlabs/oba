import * as React from "react";
import { View } from "react-native";

import { cn } from "@/lib/utils";

/** A value as a whole percentage between 0 and 100. */
function clampPercent(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(100, Math.max(0, value));
}

type ProgressSegment = {
  key: string;
  /** Share of the bar, 0 to 100; the segments together should not pass 100. */
  value: number;
  /** The ground of this segment, such as `bg-status-done`. */
  className: string;
};

type ProgressBarProps = {
  /** Read by screen readers: what the bar measures ("Sprint progress"). */
  label: string;
  /** 0 to 100, for a single fill. */
  value?: number;
  /** The fill colour: `primary` by default. */
  tone?: "primary" | "success" | "warning" | "destructive";
  /** Several fills side by side instead of `value`, such as tickets per status. */
  segments?: ProgressSegment[];
  className?: string;
};

const TONE_CLASS = {
  primary: "bg-primary",
  success: "bg-success",
  warning: "bg-warning",
  destructive: "bg-destructive",
} as const;

/** The 6 pt bar of sprint, milestone and onboarding progress on a `muted` track. */
function ProgressBar({
  label,
  value = 0,
  tone = "primary",
  segments,
  className,
}: ProgressBarProps) {
  const total = segments
    ? clampPercent(segments.reduce((sum, segment) => sum + clampPercent(segment.value), 0))
    : clampPercent(value);

  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={label}
      accessibilityValue={{ min: 0, max: 100, now: Math.round(total) }}
      className={cn("h-1.5 w-full flex-row overflow-hidden rounded-full bg-muted", className)}
    >
      {segments ? (
        segments.map((segment) => (
          <View
            key={segment.key}
            className={cn("h-full", segment.className)}
            style={{ width: `${clampPercent(segment.value)}%` }}
          />
        ))
      ) : (
        <View
          className={cn("h-full rounded-full", TONE_CLASS[tone])}
          style={{ width: `${total}%` }}
        />
      )}
    </View>
  );
}

export { clampPercent, ProgressBar };
export type { ProgressBarProps, ProgressSegment };
