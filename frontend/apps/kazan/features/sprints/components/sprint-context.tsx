"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { cn } from "@oba/ui/lib/utils";
import type { Sprint } from "@/types/sprint";
import { getDaysRemaining } from "../lib/sprint-dates";
import { SprintStatus } from "../schemas";
import { SprintSelector } from "./sprint-selector";

interface SprintContextProps {
  sprints: Sprint[];
  currentSprint: Sprint | null | undefined;
  onSprintChange: (sprintId: string | null) => void;
  onCreateSprint: () => void;
  canManage: boolean;
  onEditSprint?: (sprintId: string) => void;
  onDeleteSprint?: (sprintId: string) => void;
}

const STATE_LABEL_KEY = {
  [SprintStatus.ACTIVE]: "active",
  [SprintStatus.PLANNED]: "planned",
  [SprintStatus.COMPLETED]: "completed",
} as const;

/**
 * Which sprint a table shows, as one readout: the sprint selector, then the
 * sprint's state and, while it runs, the days left ("ACTIVE · 11D LEFT").
 * The literal dates stay in the selector's list, where sprints are compared.
 */
export function SprintContext({
  sprints,
  currentSprint,
  onSprintChange,
  onCreateSprint,
  canManage,
  onEditSprint,
  onDeleteSprint,
}: SprintContextProps) {
  const t = useTranslations("sprint");
  // One clock read per mount rather than one per render: reading Date.now()
  // in the render body is impure and can straddle a day boundary between the
  // server render and hydration.
  const now = useMemo(() => Date.now(), []);
  const daysRemaining = getDaysRemaining(currentSprint?.endDate, now);
  const showsDaysLeft = currentSprint?.status === SprintStatus.ACTIVE && daysRemaining > 0;
  // The days left take the accent only when time is genuinely short.
  const isDeadlineUrgent = daysRemaining <= 2;

  return (
    <div className="flex min-w-0 items-center gap-2">
      <SprintSelector
        sprints={sprints}
        currentSprintId={currentSprint?.id ?? null}
        onSprintChange={onSprintChange}
        onCreateSprint={onCreateSprint}
        canCreateSprint={canManage}
        onEditSprint={canManage ? onEditSprint : undefined}
        onDeleteSprint={canManage ? onDeleteSprint : undefined}
      />
      {currentSprint && (
        <span className="text-muted-foreground font-mono text-[11px] font-medium tracking-[0.08em] whitespace-nowrap uppercase tabular-nums">
          <span
            className={cn(currentSprint.status === SprintStatus.ACTIVE && "text-korur-blue-strong")}
          >
            {t(STATE_LABEL_KEY[currentSprint.status])}
          </span>
          {showsDaysLeft && (
            <>
              <span aria-hidden> · </span>
              <span className={cn(isDeadlineUrgent && "text-korur-blue-strong")}>
                {t("daysLeft", { count: daysRemaining })}
              </span>
            </>
          )}
        </span>
      )}
    </div>
  );
}
