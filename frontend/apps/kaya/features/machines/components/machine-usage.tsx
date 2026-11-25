"use client";

import { Progress } from "@oba/ui/sky/progress";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@oba/ui/sky/tooltip";
import { cn } from "@/lib/utils";

import type { MachineUsageResponse } from "../schemas";
import { usageDetailLines, usageView, windowValueText, type UsageWindowView } from "./usage-data";

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

/** A muted dash for a value the cell has none of; screen readers hear `label` instead. */
function Dash({ label }: { label: string }) {
  return (
    <span className="text-muted-foreground">
      <span aria-hidden>–</span>
      <span className="sr-only">{label}</span>
    </span>
  );
}

/** One window in a single short row: its short label, a thin bar of fixed width and the percentage. */
function CompactRow({ view }: { view: UsageWindowView }) {
  const value = windowValueText(view);
  return (
    <div className="flex items-center gap-2 text-xs">
      <span className="text-muted-foreground w-9 shrink-0">{view.shortLabel}</span>
      {value === null ? (
        <Dash label={`${view.label} not reported`} />
      ) : (
        <>
          <Progress
            value={view.kind === "used" ? view.percentage : 0}
            aria-label={`${view.label} usage`}
            className="h-1.5 w-16 shrink-0"
            indicatorClassName={view.kind === "used" ? tone(view.percentage) : undefined}
          />
          <span className={cn("tabular-nums", view.kind !== "used" && "text-muted-foreground")}>
            {value}
          </span>
        </>
      )}
    </div>
  );
}

/**
 * The usage of `MachineUsage` in at most two short rows, for a table cell:
 * when each window resets, how old the snapshot is and which machines share
 * the account show in a tooltip, which keyboard users open by focusing the cell.
 */
export function MachineUsageCompact({ usage, now, className }: MachineUsageProps) {
  const view = usageView(usage, now);
  const lines = view.kind === "unknown" ? [view.text] : usageDetailLines(view);
  return (
    <TooltipProvider delayDuration={300}>
      <Tooltip>
        <TooltipTrigger asChild>
          <div
            tabIndex={0}
            className={cn(
              "focus-visible:ring-ring flex w-fit flex-col gap-1 rounded-sm outline-none focus-visible:ring-2",
              className
            )}
          >
            {view.kind === "unknown" ? (
              <Dash label={view.text} />
            ) : (
              view.windows.map((window: UsageWindowView) => (
                <CompactRow key={window.label} view={window} />
              ))
            )}
          </div>
        </TooltipTrigger>
        <TooltipContent className="max-w-xs">
          {lines.map((line: string) => (
            <p key={line}>{line}</p>
          ))}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
