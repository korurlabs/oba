"use client";

import { Progress } from "@oba/ui/sky/progress";
import { cn } from "@/lib/utils";

import type { MachineUsageResponse } from "../schemas";
import { usageView, type UsageWindowView } from "./usage-data";

/** A bar turns amber from this share of the limit and red near the end of it. */
const WARN_AT = 75;
const ALERT_AT = 90;

function tone(percentage: number): string {
  if (percentage >= ALERT_AT) return "bg-error";
  if (percentage >= WARN_AT) return "bg-warning";
  return "bg-primary";
}

function WindowBar({ view }: { view: UsageWindowView }) {
  return (
    <div className="flex min-w-44 flex-col gap-1">
      <div className="flex items-baseline justify-between gap-2 text-xs">
        <span className="font-medium">{view.label}</span>
        {view.kind === "used" && <span className="tabular-nums">{view.percentageText}</span>}
      </div>
      <Progress
        value={view.kind === "used" ? view.percentage : 0}
        aria-label={`${view.label} usage`}
        indicatorClassName={view.kind === "used" ? tone(view.percentage) : undefined}
      />
      <span className="text-muted-foreground text-xs">
        {view.kind === "missing" ? "Not reported" : view.resetText}
      </span>
    </div>
  );
}

export type MachineUsageProps = {
  usage: MachineUsageResponse | null | undefined;
  /** The current time, renewed by the page every 30 seconds. */
  now: number;
  className?: string;
};

/**
 * How much of its Claude subscription's 5-hour and weekly limits the machine's
 * account has used, and when each resets. Limits belong to the account, so
 * machines on one account show the same values and name each other.
 */
export function MachineUsage({ usage, now, className }: MachineUsageProps) {
  const view = usageView(usage, now);
  if (view.kind === "unknown") {
    return <span className={cn("text-muted-foreground text-xs", className)}>{view.text}</span>;
  }
  return (
    <div className={cn("flex flex-col gap-2", className)}>
      {view.windows.map((window: UsageWindowView) => (
        <WindowBar key={window.label} view={window} />
      ))}
      {(view.ageText !== null || view.sharedText !== null) && (
        <div className="text-muted-foreground flex flex-col text-xs">
          {view.ageText !== null && <span>{view.ageText}</span>}
          {view.sharedText !== null && <span>{view.sharedText}</span>}
        </div>
      )}
    </div>
  );
}
