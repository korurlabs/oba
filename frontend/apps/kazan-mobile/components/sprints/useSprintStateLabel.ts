import * as React from "react";

import { sprintProgress } from "@/components/home/homeModel";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { Sprint } from "@/lib/schemas/boards";

/** "Active" or "Planned · starts Nov 3": where a sprint a ticket can go to stands. */
export function useSprintStateLabel(): (
  sprint: Pick<Sprint, "status" | "startDate" | "endDate">
) => string {
  const { t } = useTranslation();
  const dates = useDates();
  return React.useCallback(
    (sprint: Pick<Sprint, "status" | "startDate" | "endDate">): string => {
      if (sprint.status === "ACTIVE") return t("boardSprintActive");
      const { startDay } = sprintProgress(sprint.startDate, sprint.endDate);
      const starts: string | null = startDay ? dates.shortDate(startDay) : null;
      return [t("boardSprintPlanned"), starts ? t("newTicketSprintStarts", { date: starts }) : null]
        .filter(Boolean)
        .join(" · ");
    },
    [dates, t]
  );
}
