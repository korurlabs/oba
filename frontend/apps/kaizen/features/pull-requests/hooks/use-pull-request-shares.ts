"use client";

import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import type { ApiResponse } from "@oba/shared/schema";
import { CHAT_KEYS } from "@/features/chat/hooks/query-keys";
import { useChatOrganizationId } from "@/features/chat/hooks/use-chat-messages";
import { isMemberWithoutKaizenAccessError } from "@/lib/kaizen-member-access";
import {
  approvePullRequestShare,
  createPullRequestShare,
  deletePullRequestShare,
  editPullRequestShare,
  getPullRequestShareById,
  withdrawPullRequestShareApproval,
} from "../api";
import type {
  PullRequestShareCreateRequest,
  PullRequestShareResponse,
  PullRequestShareUpdateRequest,
} from "../schemas";
import { PULL_REQUEST_KEYS } from "./query-keys";

/**
 * Every share is also a message of the PR channel, which shows its card. The
 * realtime signal refreshes the channel too, but only while the socket is up;
 * this keeps the writer's own channel current without it.
 */
async function invalidateShareAndChannel(queryClient: QueryClient, orgId: string) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: PULL_REQUEST_KEYS.sharesAll(orgId) }),
    queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channelMessagesAll(orgId) }),
  ]);
}

/**
 * The share's answer replaces the cached copy at once, so the edit screen and
 * the approval buttons show the new state before the refetch returns.
 */
function storeShare(queryClient: QueryClient, orgId: string, response: ApiResponse<PullRequestShareResponse>) {
  if (response.data) {
    queryClient.setQueryData(PULL_REQUEST_KEYS.share(orgId, response.data.id), response);
  }
}

/** One share; disabled until both the organization and `shareId` are known. */
export function usePullRequestShare(shareId: string | undefined) {
  const orgId = useChatOrganizationId();
  return useQuery({
    queryKey: PULL_REQUEST_KEYS.share(orgId ?? "", shareId ?? ""),
    queryFn: () => getPullRequestShareById(shareId as string),
    enabled: !!orgId && !!shareId,
  });
}

/**
 * The toast text of a refused create or edit: a reviewer without Kaizen
 * access gets the translated reason, any other refusal the server's message.
 */
function shareErrorMessage(error: Error, tAccess: (key: string) => string): string {
  return isMemberWithoutKaizenAccessError(error) ? tAccess("refused") : error.message;
}

/**
 * Shares a pull request and posts its card in the PR channel. A refusal
 * shows the server's message; the form keeps its values because the caller
 * navigates away only on success.
 */
export function useCreatePullRequestShare() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("pullRequests");
  const tAccess = useTranslations("kaizen.memberAccess");

  return useMutation({
    mutationFn: (data: PullRequestShareCreateRequest) => createPullRequestShare(data),
    onSuccess: async (response) => {
      toast.success(t("messages.shared"));
      if (!orgId) return;
      storeShare(queryClient, orgId, response);
      await invalidateShareAndChannel(queryClient, orgId);
    },
    onError: (error: Error) => {
      toast.error(shareErrorMessage(error, tAccess));
    },
  });
}

/** The author's edit of status, note and reviewers. */
export function useEditPullRequestShare() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("pullRequests");
  const tAccess = useTranslations("kaizen.memberAccess");

  return useMutation({
    mutationFn: ({ shareId, data }: { shareId: string; data: PullRequestShareUpdateRequest }) =>
      editPullRequestShare(shareId, data),
    onSuccess: async (response) => {
      toast.success(t("messages.updated"));
      if (!orgId) return;
      storeShare(queryClient, orgId, response);
      await invalidateShareAndChannel(queryClient, orgId);
    },
    onError: (error: Error) => {
      toast.error(shareErrorMessage(error, tAccess));
    },
  });
}

/** Removes the author's share and its card from the PR channel. */
export function useDeletePullRequestShare() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("pullRequests");

  return useMutation({
    mutationFn: (shareId: string) => deletePullRequestShare(shareId),
    onSuccess: async (_, shareId) => {
      toast.success(t("messages.deleted"));
      if (!orgId) return;
      // Dropped first, so an open edit screen does not refetch a 404.
      const detailKey = PULL_REQUEST_KEYS.share(orgId, shareId);
      queryClient.removeQueries({ queryKey: detailKey });
      await invalidateShareAndChannel(queryClient, orgId);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

/** The reader approves as a reviewer; approving twice changes nothing. */
export function useApprovePullRequestShare() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("pullRequests");

  return useMutation({
    mutationFn: (shareId: string) => approvePullRequestShare(shareId),
    onSuccess: async (response) => {
      toast.success(t("messages.approved"));
      if (!orgId) return;
      storeShare(queryClient, orgId, response);
      await invalidateShareAndChannel(queryClient, orgId);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

/** The reader withdraws their approval; withdrawing an absent approval changes nothing. */
export function useWithdrawPullRequestShareApproval() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("pullRequests");

  return useMutation({
    mutationFn: (shareId: string) => withdrawPullRequestShareApproval(shareId),
    onSuccess: async (response) => {
      toast.success(t("messages.withdrawn"));
      if (!orgId) return;
      storeShare(queryClient, orgId, response);
      await invalidateShareAndChannel(queryClient, orgId);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}
