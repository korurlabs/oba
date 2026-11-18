"use client";

import { useMemo } from "react";
import { useLocale } from "next-intl";
import type { SelectOption } from "@oba/shared/features/form/components";
import { MilestoneStatus, type MilestoneListParams } from "../schemas";
import { formatDateOnly } from "@oba/shared/lib/date-only";
import { useMilestones } from "./use-milestones";

/** OPEN milestones of the board and of every board a ticket on it may attach to. */
export const ATTACHABLE_MILESTONE_PARAMS: MilestoneListParams = {
  status: MilestoneStatus.OPEN,
  includeRelatedBoards: true,
};

/** A milestone the option list must contain even when it is not attachable any more. */
export type PinnedMilestone = {
  id: string;
  name: string;
  targetDate: string;
  boardId: string;
};

/**
 * Select options for a milestone picker. Names are not unique, so the value is
 * always the id and the label carries the target date; the group heading
 * carries the board, so two "Beta" milestones on different boards or dates
 * stay distinguishable in the list and in the closed trigger.
 *
 * `pinned` keeps the ticket's current milestone selectable when it is CLOSED
 * and therefore missing from the OPEN list.
 */
export function useMilestoneOptions(
  boardId: string | undefined,
  options?: { pinned?: PinnedMilestone | null; enabled?: boolean },
) {
  const locale = useLocale();
  const query = useMilestones(boardId ?? "", ATTACHABLE_MILESTONE_PARAMS, {
    enabled: options?.enabled ?? true,
  });
  const milestones = query.data?.data;
  const pinned = options?.pinned;

  const selectOptions = useMemo<SelectOption[]>(() => {
    const list = milestones ?? [];
    // Groups render in the order they first appear: the board's own
    // milestones first, then the related boards by name. The sort is stable,
    // so the server's target-date order holds inside each group.
    const sorted = [...list].sort((a, b) => {
      const aOwn = a.boardId === boardId ? 0 : 1;
      const bOwn = b.boardId === boardId ? 0 : 1;
      return aOwn - bOwn || a.boardName.localeCompare(b.boardName);
    });
    const result: SelectOption[] = sorted.map((m) => ({
      value: m.id,
      label: `${m.name} · ${formatDateOnly(m.targetDate, locale)}`,
      group: `${m.boardName} (${m.boardPrefix})`,
      meta: { boardId: m.boardId, milestone: m },
    }));
    if (pinned && !list.some((m) => m.id === pinned.id)) {
      result.unshift({
        value: pinned.id,
        label: `${pinned.name} · ${formatDateOnly(pinned.targetDate, locale)}`,
        meta: { boardId: pinned.boardId },
      });
    }
    return result;
  }, [milestones, pinned, locale, boardId]);

  return { options: selectOptions, milestones: milestones ?? [], isLoading: query.isLoading };
}
