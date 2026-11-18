"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  addMilestoneDependency,
  closeMilestone,
  createMilestone,
  deleteMilestone,
  getMilestoneById,
  getRoadmap,
  linkMilestoneTickets,
  listMilestones,
  removeMilestoneDependency,
  reopenMilestone,
  unlinkMilestoneTickets,
  updateMilestone,
} from "../api";
import type {
  MilestoneCreateRequest,
  MilestoneListParams,
  MilestoneTicketsRequest,
  MilestoneUpdateRequest,
  RoadmapParams,
} from "../schemas";
import { invalidateRelatedQueries } from "@/hooks/invalidate-related-queries";
import { MILESTONE_KEYS, ROADMAP_KEYS } from "./query-keys";

/**
 * Keys live in the boundary-safe `./query-keys` module so the roadmap page can
 * prefetch under them on the server. Re-exported for client consumers.
 */
export { MILESTONE_KEYS, ROADMAP_KEYS };

// Every milestone write changes what tickets show (their milestone badge) and
// the counts and health of the milestone and roadmap queries, all of which
// `invalidateRelatedQueries` refreshes.

export function useMilestones(
  boardId: string,
  params: MilestoneListParams = {},
  options?: { enabled?: boolean },
) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: MILESTONE_KEYS.list(boardId, params),
    queryFn: () => listMilestones(boardId, params),
    enabled: !!boardId && enabled,
  });
}

export function useMilestone(boardId: string | undefined, milestoneId: string | undefined) {
  return useQuery({
    queryKey: MILESTONE_KEYS.detail(boardId ?? "", milestoneId ?? ""),
    queryFn: () => getMilestoneById(boardId as string, milestoneId as string),
    enabled: !!boardId && !!milestoneId,
  });
}

export function useRoadmap(boardId: string, params: RoadmapParams = {}) {
  return useQuery({
    queryKey: ROADMAP_KEYS.board(boardId, params),
    queryFn: () => getRoadmap(boardId, params),
    enabled: !!boardId,
  });
}

export function useCreateMilestone() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ boardId, data }: { boardId: string; data: MilestoneCreateRequest }) =>
      createMilestone(boardId, data),
    onSuccess: () => {
      toast.success(t("milestones.messages.created"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useUpdateMilestone() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      milestoneId,
      data,
    }: {
      boardId: string;
      milestoneId: string;
      data: MilestoneUpdateRequest;
    }) => updateMilestone(boardId, milestoneId, data),
    onSuccess: () => {
      toast.success(t("milestones.messages.updated"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useCloseMilestone() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ boardId, milestoneId }: { boardId: string; milestoneId: string }) =>
      closeMilestone(boardId, milestoneId),
    onSuccess: () => {
      toast.success(t("milestones.messages.closed"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useReopenMilestone() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ boardId, milestoneId }: { boardId: string; milestoneId: string }) =>
      reopenMilestone(boardId, milestoneId),
    onSuccess: () => {
      toast.success(t("milestones.messages.reopened"));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useLinkMilestoneTickets() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      milestoneId,
      data,
    }: {
      boardId: string;
      milestoneId: string;
      data: MilestoneTicketsRequest;
    }) => linkMilestoneTickets(boardId, milestoneId, data),
    onSuccess: (_, variables) => {
      toast.success(t("milestones.messages.linked", { count: variables.data.ticketIds.length }));
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useUnlinkMilestoneTickets() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      milestoneId,
      data,
    }: {
      boardId: string;
      milestoneId: string;
      data: MilestoneTicketsRequest;
    }) => unlinkMilestoneTickets(boardId, milestoneId, data),
    onSuccess: (_, variables) => {
      toast.success(
        t("milestones.messages.unlinked", { count: variables.data.ticketIds.length }),
      );
      invalidateRelatedQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useDeleteMilestone(options?: {
  /** Runs after the detail cache is removed and before invalidation, so an
   *  open sheet can close before its detail query would refetch a 404. */
  onBeforeInvalidate?: () => void;
}) {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ boardId, milestoneId }: { boardId: string; milestoneId: string }) =>
      deleteMilestone(boardId, milestoneId),
    onSuccess: async (_, variables) => {
      toast.success(t("milestones.messages.deleted"));
      const detailKey = MILESTONE_KEYS.detail(variables.boardId, variables.milestoneId);
      queryClient.removeQueries({ queryKey: detailKey });
      options?.onBeforeInvalidate?.();
      await invalidateRelatedQueries(queryClient, { excludeKeys: [detailKey] });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

type DependencyVariables = { boardId: string; milestoneId: string; dependsOnId: string };

// A dependency changes no ticket, only the milestone and roadmap responses
// that carry `dependsOn`, so only those two caches are refreshed.
async function invalidateMilestoneViews(queryClient: ReturnType<typeof useQueryClient>) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: MILESTONE_KEYS.all }),
    queryClient.invalidateQueries({ queryKey: ROADMAP_KEYS.all }),
  ]);
}

export function useAddMilestoneDependency() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ boardId, milestoneId, dependsOnId }: DependencyVariables) =>
      addMilestoneDependency(boardId, milestoneId, dependsOnId),
    onSuccess: () => {
      toast.success(t("milestones.messages.dependencyAdded"));
      invalidateMilestoneViews(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useRemoveMilestoneDependency() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({ boardId, milestoneId, dependsOnId }: DependencyVariables) =>
      removeMilestoneDependency(boardId, milestoneId, dependsOnId),
    onSuccess: () => {
      toast.success(t("milestones.messages.dependencyRemoved"));
      invalidateMilestoneViews(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}
