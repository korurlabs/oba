"use client";

import { useLocale, useTranslations } from "next-intl";
import { Flag } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import type { MilestoneHealth, MilestoneStatus } from "../schemas";
import { formatDateOnly } from "@oba/shared/lib/date-only";
import { MILESTONE_HEALTH_STYLES } from "../lib/milestone-format";

type MilestoneBadgeProps = {
  milestone: { id: string; name: string; status: MilestoneStatus | string; targetDate: string };
  size?: "sm" | "md";
  className?: string;
};

/**
 * Milestone a ticket is attached to. Names are not unique, so the target date
 * is always shown next to the name: two milestones called "Beta" on one board
 * stay distinguishable on the card.
 */
export function MilestoneBadge({ milestone, size = "md", className }: MilestoneBadgeProps) {
  const locale = useLocale();
  const t = useTranslations("milestone");
  const date = formatDateOnly(milestone.targetDate, locale);
  const isClosed = milestone.status === "CLOSED";

  return (
    <span
      className={cn(
        "border-border text-foreground inline-flex max-w-full items-center rounded-full border font-medium",
        size === "sm" ? "gap-0.5 px-1.5 py-0.5 text-[10px]" : "gap-1 px-2 py-0.5 text-xs",
        isClosed && "text-muted-foreground",
        className,
      )}
      title={t("badgeTitle", { name: milestone.name, date })}
    >
      <Flag className={cn("shrink-0", size === "sm" ? "h-2.5 w-2.5" : "h-3 w-3")} aria-hidden />
      <span className="truncate">{milestone.name}</span>
      <span className="text-muted-foreground shrink-0">· {date}</span>
    </span>
  );
}

export function MilestoneHealthBadge({
  health,
  className,
}: {
  health: MilestoneHealth;
  className?: string;
}) {
  const t = useTranslations("milestone.health");
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium",
        MILESTONE_HEALTH_STYLES[health].badge,
        className,
      )}
    >
      {t(health)}
    </span>
  );
}
