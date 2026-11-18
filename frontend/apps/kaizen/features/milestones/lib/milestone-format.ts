import type { MilestoneHealth } from "../schemas";

/**
 * Health colours from the theme tokens. Health is always shown as text too,
 * so the colour is never the only signal.
 */
export const MILESTONE_HEALTH_STYLES: Record<
  MilestoneHealth,
  { bar: string; text: string; badge: string }
> = {
  ON_TRACK: {
    bar: "bg-success",
    text: "text-success",
    badge: "border-success/40 bg-success/10 text-success",
  },
  AT_RISK: {
    bar: "bg-warning",
    text: "text-warning",
    badge: "border-warning/40 bg-warning/10 text-warning",
  },
  OVERDUE: {
    bar: "bg-destructive",
    text: "text-destructive",
    badge: "border-destructive/40 bg-destructive/10 text-destructive",
  },
  DONE: {
    bar: "bg-muted-foreground",
    text: "text-muted-foreground",
    badge: "border-border bg-muted text-muted-foreground",
  },
};
