import { Badge, type badgeVariants } from "@oba/ui/sky/badge";
import { cn } from "@/lib/utils";
import type { VariantProps } from "class-variance-authority";

type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>["variant"]>;

/**
 * Every state a badge can show: a session's or a command's progress, and
 * "done" for a whole session whose work is finished.
 */
export type BadgeState = "pending" | "running" | "waiting" | "failed" | "passed" | "done";

type StateTone = {
  label: string;
  variant: BadgeVariant;
  /** Fill of a stepper dot or another small marker in this state. */
  dot: string;
};

/**
 * Label and color per state. The colors are the semantic tokens, so a state
 * reads the same on the wall, the session view and the timeline: success for
 * passed, info for running, warning for waiting, error for failed, muted for
 * pending.
 */
export const STATE_TONES: Readonly<Record<BadgeState, StateTone>> = {
  pending: { label: "Pending", variant: "muted", dot: "bg-muted-foreground/25" },
  running: { label: "Running", variant: "info", dot: "bg-info" },
  waiting: { label: "Waiting for you", variant: "warning", dot: "bg-warning" },
  failed: { label: "Failed", variant: "error", dot: "bg-error" },
  passed: { label: "Passed", variant: "success", dot: "bg-success" },
  done: { label: "Done", variant: "success", dot: "bg-success" },
};

export type StateBadgeProps = {
  state: BadgeState;
  className?: string;
};

/** A session's or a command's state as a colored badge. */
export function StateBadge({ state, className }: StateBadgeProps) {
  const tone = STATE_TONES[state];
  return (
    <Badge variant={tone.variant} className={cn("font-medium", className)}>
      {state === "running" && (
        <span aria-hidden className="bg-info size-1.5 animate-pulse rounded-full" />
      )}
      {tone.label}
    </Badge>
  );
}
