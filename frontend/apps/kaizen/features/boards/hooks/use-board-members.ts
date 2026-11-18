"use client";

import { useMutation, useQuery, useQueries, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { isMemberWithoutKaizenAccessError } from "@/lib/kaizen-member-access";
import {
  listBoardMembers,
  listBoardMemberCandidates,
  addBoardMember,
  removeBoardMember,
  updateBoardMember,
  updateBoardMemberSpecialties,
} from "../api/board-members";
import type {
  BoardMemberAddRequest,
  BoardMemberUpdateRequest,
  BoardMemberSpecialtyUpdateRequest,
} from "../schemas";

const KEYS = {
  all: ["boardMembers"] as const,
  list: (boardId: string) => [...KEYS.all, "list", boardId] as const,
  candidates: (boardId: string) => [...KEYS.all, "candidates", boardId] as const,
};

/**
 * The toast text of a failed member write: a member without Kaizen access
 * gets the translated reason, any other failure the server's message.
 */
function boardMemberErrorMessage(error: Error, t: (key: string) => string): string {
  return isMemberWithoutKaizenAccessError(error) ? t("memberAccess.refused") : error.message;
}

export function useBoardMembers(
  boardId: string,
  options?: { enabled?: boolean }
) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.list(boardId),
    queryFn: () => listBoardMembers(boardId),
    enabled: !!boardId && enabled,
  });
}

/**
 * The organization's active members the "Add Member" picker offers, each with
 * `kaizenAccess`. Under the board members key, so an add or a removal
 * refreshes it.
 */
export function useBoardMemberCandidates(
  boardId: string,
  options?: { enabled?: boolean }
) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.candidates(boardId),
    queryFn: () => listBoardMemberCandidates(boardId),
    enabled: !!boardId && enabled,
  });
}

export function useMultipleBoardMembers(boardIds: string[]) {
  return useQueries({
    queries: boardIds.map((boardId) => ({
      queryKey: KEYS.list(boardId),
      queryFn: () => listBoardMembers(boardId),
      enabled: !!boardId,
    })),
  });
}

export function useAddBoardMember() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      data,
    }: {
      boardId: string;
      data: BoardMemberAddRequest;
    }) => addBoardMember(boardId, data),
    onSuccess: () => {
      toast.success(t("boardMembers.messages.added"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(boardMemberErrorMessage(error, t));
    },
  });
}

export function useRemoveBoardMember() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({ boardId, userId }: { boardId: string; userId: string }) =>
      removeBoardMember(boardId, userId),
    onSuccess: () => {
      toast.success(t("boardMembers.messages.removed"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useUpdateBoardMember() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      userId,
      data,
    }: {
      boardId: string;
      userId: string;
      data: BoardMemberUpdateRequest;
    }) => updateBoardMember(boardId, userId, data),
    onSuccess: () => {
      toast.success(t("boardMembers.messages.updated"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useUpdateBoardMemberSpecialties() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      userId,
      data,
    }: {
      boardId: string;
      userId: string;
      data: BoardMemberSpecialtyUpdateRequest;
    }) => updateBoardMemberSpecialties(boardId, userId, data),
    onSuccess: () => {
      toast.success(t("boardMembers.messages.specialtiesUpdated"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}
