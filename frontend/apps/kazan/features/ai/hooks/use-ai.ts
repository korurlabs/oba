"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  getAiAvailability,
  analyzeWithAi,
  createFromSuggestions,
} from "../api";
import type {
  AiAnalyzeRequest,
  AiCreateFromSuggestionsRequest,
} from "../schemas";

const KEYS = {
  all: ["ai"] as const,
  available: (boardId: string) => [...KEYS.all, "available", boardId] as const,
};

export function useAiAvailability(
  boardId: string,
  options?: { enabled?: boolean }
) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.available(boardId),
    queryFn: () => getAiAvailability(boardId),
    enabled: !!boardId && enabled,
  });
}

export function useAnalyzeWithAi() {
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      data,
    }: {
      boardId: string;
      data: AiAnalyzeRequest;
    }) => analyzeWithAi(boardId, data),
    onError: () => {
      toast.error(t("ai.messages.analyzeError"));
    },
  });
}

export function useCreateFromSuggestions() {
  const queryClient = useQueryClient();
  const t = useTranslations("kazan");

  return useMutation({
    mutationFn: ({
      boardId,
      data,
    }: {
      boardId: string;
      data: AiCreateFromSuggestionsRequest;
    }) => createFromSuggestions(boardId, data),
    onSuccess: () => {
      toast.success(t("ai.messages.ticketsCreated"));
      queryClient.invalidateQueries({ queryKey: ["tickets"] });
      queryClient.invalidateQueries({ queryKey: ["backlog"] });
    },
    onError: () => {
      toast.error(t("ai.messages.createError"));
    },
  });
}
