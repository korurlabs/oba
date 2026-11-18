"use client";

import { cn } from "@oba/ui/lib/utils";
import { ChevronDown } from "lucide-react";
import { useTranslations } from "next-intl";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import type { SubTicketProgress } from "@/lib/utils/ticket-groups";

interface SubTicketProgressBarProps {
  completed: number;
  total: number;
  /**
   * Splits the unfinished part by placement: in the sprint shown, in other
   * sprints, in the backlog. A sub-ticket can be planned apart from its
   * parent, so "not done" alone hides where the remaining work is.
   */
  breakdown?: SubTicketProgress;
  onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  size?: "sm" | "md";
  showLabel?: boolean;
  className?: string;
}

const SEGMENTS = [
  { key: "done", className: "bg-green-500" },
  { key: "inThisSprint", className: "bg-primary" },
  { key: "inOtherSprints", className: "bg-amber-400" },
  { key: "inBacklog", className: "bg-gray-400" },
] as const;

export function SubTicketProgressBar({
  completed,
  total,
  breakdown,
  onClick,
  size = "sm",
  showLabel = true,
  className,
}: SubTicketProgressBarProps) {
  const t = useTranslations("ticket.inlineSubTickets");

  if (total === 0) return null;

  const percentage = Math.round((completed / total) * 100);
  const trackClassName = cn(
    "flex overflow-hidden rounded-full bg-gray-200",
    size === "sm" ? "h-1 w-full" : "h-1.5 w-full"
  );

  const bar = breakdown ? (
    <div
      className={trackClassName}
      role="progressbar"
      aria-valuenow={percentage}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuetext={t("progress.summary", {
        done: breakdown.done,
        inThisSprint: breakdown.inThisSprint,
        inOtherSprints: breakdown.inOtherSprints,
        inBacklog: breakdown.inBacklog,
        total: breakdown.total,
      })}
    >
      {SEGMENTS.map((segment) =>
        breakdown[segment.key] > 0 ? (
          <div
            key={segment.key}
            className={cn("h-full", segment.className)}
            style={{ width: `${(breakdown[segment.key] / total) * 100}%` }}
          />
        ) : null
      )}
    </div>
  ) : (
    <div
      className={trackClassName}
      role="progressbar"
      aria-valuenow={percentage}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <div
        className={cn(
          "h-full rounded-full transition-all duration-300",
          completed === total ? "bg-green-500" : "bg-primary"
        )}
        style={{ width: `${percentage}%` }}
      />
    </div>
  );

  const label = showLabel && (
    <span className={cn("font-medium", size === "sm" ? "text-[10px]" : "text-xs")}>
      {completed}/{total}
    </span>
  );

  const containerClassName = cn(
    "flex items-center gap-1.5 w-full justify-between text-gray-500 transition-colors hover:text-gray-700",
    onClick && "cursor-pointer",
    className
  );

  const content = onClick ? (
    <button type="button" onClick={onClick} className={containerClassName} aria-label={t("toggle")}>
      {bar}
      {label}
      <ChevronDown className="h-3 w-3" />
    </button>
  ) : (
    <div className={containerClassName}>
      {bar}
      {label}
    </div>
  );

  if (!breakdown) return content;

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>{content}</TooltipTrigger>
        <TooltipContent>
          <ul className="space-y-0.5 text-xs">
            {SEGMENTS.map((segment) => (
              <li key={segment.key} className="flex items-center gap-1.5">
                <span className={cn("h-2 w-2 rounded-full", segment.className)} />
                {t(`progress.${segment.key}`, { count: breakdown[segment.key] })}
              </li>
            ))}
          </ul>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
