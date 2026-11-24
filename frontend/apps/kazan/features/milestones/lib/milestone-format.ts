import type { MilestoneHealth } from "../schemas";

/**
 * Health in Kazan's grid inks: primary for on track, plain ink with a dashed
 * rim for at risk, red only for overdue, muted once done. Health is always
 * shown as text too, so the ink is never the only signal.
 */
export const MILESTONE_HEALTH_STYLES: Record<
  MilestoneHealth,
  { bar: string; text: string; badge: string }
> = {
  ON_TRACK: {
    bar: "bg-primary",
    text: "text-korur-blue-strong",
    badge: "border-primary/50 text-korur-blue-strong",
  },
  AT_RISK: {
    bar: "bg-foreground/50",
    text: "text-foreground",
    badge: "border-foreground/40 border-dashed text-foreground",
  },
  OVERDUE: {
    bar: "bg-destructive",
    text: "text-destructive",
    badge: "border-destructive/50 text-destructive",
  },
  DONE: {
    bar: "bg-muted-foreground",
    text: "text-muted-foreground",
    badge: "border-border text-muted-foreground",
  },
};
