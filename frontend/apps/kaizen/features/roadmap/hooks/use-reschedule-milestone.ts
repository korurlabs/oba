"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import type { ApiResponse } from "@oba/shared/schema";
import { updateMilestone } from "@/features/milestones/api";
import { ROADMAP_KEYS, ROADMAP_PAGE_PARAMS } from "@/features/milestones/hooks/query-keys";
import type {
  MilestoneListResponse,
  MilestoneUpdateRequest,
  RoadmapResponse,
} from "@/features/milestones/schemas";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import { isDependencyConflict } from "../lib/roadmap-layout";

export type RescheduleVariables = {
  milestone: MilestoneListResponse;
  /** `YYYY-MM-DD`; null keeps a milestone without a start date. */
  startDate: string | null;
  targetDate: string;
};

type RescheduleContext = { previous: ApiResponse<RoadmapResponse> | undefined };

/**
 * Saves new dates for a milestone dragged on the roadmap of `pageBoardId`.
 * The roadmap cache shows the new dates at once, with the dependency
 * conflicts they cause, and goes back to the old ones when the server
 * refuses. Only the changed dates are sent.
 */
export function useRescheduleMilestone(pageBoardId: string) {
  const queryClient = useQueryClient();
  const t = useTranslations("roadmap");
  const queryKey = ROADMAP_KEYS.board(pageBoardId, ROADMAP_PAGE_PARAMS);

  return useMutation<ApiResponse<unknown>, Error, RescheduleVariables, RescheduleContext>({
    mutationFn: ({ milestone, startDate, targetDate }) => {
      const data: MilestoneUpdateRequest = {};
      if (startDate !== null && startDate !== milestone.startDate) data.startDate = startDate;
      if (targetDate !== milestone.targetDate) data.targetDate = targetDate;
      return updateMilestone(milestone.boardId, milestone.id, data);
    },
    onMutate: async (variables) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<ApiResponse<RoadmapResponse>>(queryKey);
      if (previous?.data) {
        queryClient.setQueryData<ApiResponse<RoadmapResponse>>(queryKey, {
          ...previous,
          data: patchRoadmapDates(previous.data, variables),
        });
      }
      return { previous };
    },
    onError: (error, variables, context) => {
      if (context?.previous) queryClient.setQueryData(queryKey, context.previous);
      toast.error(t("rescheduleError", { name: variables.milestone.name }), {
        description: error.message,
      });
    },
    // The new dates change health, ticket badges and the milestone lists, so
    // everything that shows a milestone is refreshed from the server.
    onSettled: () => invalidateRelatedQueries(queryClient),
  });
}

/** The roadmap with one milestone's dates replaced and every affected conflict recomputed. */
function patchRoadmapDates(
  roadmap: RoadmapResponse,
  { milestone, startDate, targetDate }: RescheduleVariables,
): RoadmapResponse {
  const newStart = startDate ?? milestone.startDate ?? null;
  return {
    ...roadmap,
    boards: roadmap.boards.map((board) => ({
      ...board,
      milestones: board.milestones.map((m) => {
        if (m.id === milestone.id) {
          return {
            ...m,
            startDate: newStart,
            targetDate,
            dependsOn: m.dependsOn.map((d) => ({
              ...d,
              conflict: isDependencyConflict(newStart, targetDate, d.targetDate),
            })),
          };
        }
        if (!m.dependsOn.some((d) => d.milestoneId === milestone.id)) return m;
        return {
          ...m,
          dependsOn: m.dependsOn.map((d) =>
            d.milestoneId === milestone.id
              ? {
                  ...d,
                  targetDate,
                  conflict: isDependencyConflict(m.startDate, m.targetDate, targetDate),
                }
              : d,
          ),
        };
      }),
    })),
  };
}
