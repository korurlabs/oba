"use client";

import { useMutation, useQuery, useQueries, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  listLabels,
  createLabel,
  updateLabel,
  deleteLabel,
} from "../api";
import type { LabelCreateRequest, LabelUpdateRequest } from "../schemas";

const KEYS = {
  all: ["labels"] as const,
  list: (boardId: string) => [...KEYS.all, "list", boardId] as const,
};

export function useLabels(boardId: string) {
  return useQuery({
    queryKey: KEYS.list(boardId),
    queryFn: () => listLabels(boardId),
    enabled: !!boardId,
  });
}

/**
 * The label lists of several boards, one request per board under the same
 * key as useLabels, so a board whose labels are already cached is not
 * fetched again.
 */
export function useMultipleBoardLabels(boardIds: string[]) {
  return useQueries({
    queries: boardIds.map((boardId) => ({
      queryKey: KEYS.list(boardId),
      queryFn: () => listLabels(boardId),
      enabled: !!boardId,
    })),
  });
}

export function useCreateLabel() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      data,
    }: {
      boardId: string;
      data: LabelCreateRequest;
    }) => createLabel(boardId, data),
    onSuccess: () => {
      toast.success(t("labels.messages.created"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: () => {
      toast.error(t("labels.messages.createError"));
    },
  });
}

export function useUpdateLabel() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      labelId,
      data,
    }: {
      boardId: string;
      labelId: string;
      data: LabelUpdateRequest;
    }) => updateLabel(boardId, labelId, data),
    onSuccess: () => {
      toast.success(t("labels.messages.updated"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: () => {
      toast.error(t("labels.messages.updateError"));
    },
  });
}

export function useDeleteLabel() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      labelId,
    }: {
      boardId: string;
      labelId: string;
    }) => deleteLabel(boardId, labelId),
    onSuccess: () => {
      toast.success(t("labels.messages.deleted"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: () => {
      toast.error(t("labels.messages.deleteError"));
    },
  });
}
