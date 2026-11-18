"use client";

import { useEffect, useState } from "react";
import {
  hashKey,
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
  type QueryKey,
} from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { CHAT_KEYS } from "@/features/chat/hooks/query-keys";
import { useChatOrganizationId } from "@/features/chat/hooks/use-chat-messages";
import { TICKET_KEYS } from "@/features/tickets/hooks/query-keys";
import {
  createUpdate,
  deleteUpdate,
  editUpdate,
  getMissingUpdates,
  getPreviousUpdate,
  getUpdateById,
  previewUpdate,
  previewUpdateEdit,
} from "../api";
import type { UpdateCreateRequest, UpdateEditRequest } from "../schemas";
import { isOrganizationAdmin } from "../lib/organization-admin";
import { updatePreviousDay, updateToday } from "../lib/update-day";
import { UPDATE_KEYS } from "./query-keys";

/**
 * Keys live in the boundary-safe `./query-keys` module so the update pages
 * can prefetch under them on the server. Re-exported for client consumers.
 */
export { UPDATE_KEYS };

/**
 * Every daily update is also a message of the Updates channel, which shows
 * its card. The realtime signal refreshes the channel too, but only while the
 * socket is up; this keeps the author's own channel current without it.
 */
async function invalidateChannel(queryClient: QueryClient, orgId: string | undefined) {
  if (orgId) await queryClient.invalidateQueries({ queryKey: CHAT_KEYS.channelMessagesAll(orgId) });
}

// Create and edit may write a line's percentage back to its ticket, so every
// ticket list ("tickets" is the prefix of the board and all-tickets keys) is
// refreshed with the updates. Delete never reverts ticket progress.
async function invalidateAfterWrite(queryClient: QueryClient, orgId: string | undefined) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: UPDATE_KEYS.all }),
    queryClient.invalidateQueries({ queryKey: TICKET_KEYS.all }),
    invalidateChannel(queryClient, orgId),
  ]);
}

export function useUpdate(updateId: string | undefined) {
  return useQuery({
    queryKey: UPDATE_KEYS.detail(updateId ?? ""),
    queryFn: () => getUpdateById(updateId as string),
    enabled: !!updateId,
  });
}

/**
 * Drops the given queries from the cache once the screen that read them is
 * gone, so the next visit starts from the page's fresh server prefetch.
 *
 * Seeds of a new update must reflect each ticket's current progress: a
 * percentage left unchanged on a line is written back to its ticket. A
 * cached copy would be served on the next visit's first render, before
 * `HydrationBoundary` applies newer server data to queries already in the
 * cache, and the form freezes its seed on that render. A `gcTime` on the
 * hook does not help, because a query created by hydration keeps the
 * client's default.
 *
 * The removal waits a tick and skips queries that are still observed, so
 * React's development double mount, which re-subscribes straight away, does
 * not drop the data of a screen that is still open.
 */
export function useDiscardQueriesOnLeave(queryKeys: readonly QueryKey[]) {
  const queryClient = useQueryClient();
  const keysHash = hashKey(queryKeys);

  useEffect(() => {
    const keys = queryKeys;
    return () => {
      setTimeout(() => {
        keys.forEach((queryKey) => {
          queryClient.removeQueries({ queryKey, exact: true, type: "inactive" });
        });
      }, 0);
    };
    // The keys are compared by value through their hash.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryClient, keysHash]);
}

/**
 * Who has not posted in `organizationId` on `date` (null = today). Enabled
 * only when the cached "me" lists the reader as OWNER or ADMIN of that
 * organization, so while "me" loads or failed, and for any other role, no
 * request is sent. The server's 403 still covers a role changed since "me"
 * was fetched.
 */
export function useMissingUpdates(organizationId: string | undefined, date: string | null) {
  const { data: meResponse } = useGetMe();
  const me = meResponse?.success ? meResponse.data : undefined;
  return useQuery({
    queryKey: UPDATE_KEYS.missing(organizationId ?? "", date),
    queryFn: () => getMissingUpdates({ organizationId: organizationId as string, date }),
    enabled: !!organizationId && isOrganizationAdmin(me, organizationId),
  });
}

/**
 * The author's latest update created before today's report day, so an update
 * already posted today is never offered as the previous one. The day is fixed
 * when the screen mounts, so a screen left open past midnight keeps the same
 * previous update. Callers read `data?.data?.[0] ?? null`; null means the
 * author has no earlier update.
 */
export function usePreviousUpdate(authorId: string | undefined) {
  const [beforeDay] = useState(() => updatePreviousDay(updateToday()));
  return useQuery({
    queryKey: UPDATE_KEYS.previous(authorId ?? "", beforeDay),
    queryFn: () => getPreviousUpdate(authorId as string, beforeDay),
    enabled: !!authorId,
  });
}

/**
 * Text of a new update. A query rather than a mutation, so a slower answer to
 * an older request can never replace the newest one. The caller debounces
 * the request and passes `null` while the form is invalid; nothing is saved.
 */
export function usePreviewUpdate(request: UpdateCreateRequest | null) {
  return useQuery({
    queryKey: UPDATE_KEYS.preview(request),
    queryFn: () => previewUpdate(request as UpdateCreateRequest),
    enabled: !!request,
    placeholderData: keepPreviousData,
  });
}

/** Text of an edit of the caller's own update; same contract as `usePreviewUpdate`. */
export function usePreviewUpdateEdit(updateId: string | undefined, request: UpdateEditRequest | null) {
  return useQuery({
    queryKey: UPDATE_KEYS.previewEdit(updateId ?? "", request),
    queryFn: () => previewUpdateEdit(updateId as string, request as UpdateEditRequest),
    enabled: !!updateId && !!request,
    placeholderData: keepPreviousData,
  });
}

export function useCreateUpdate() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("updates");

  return useMutation({
    mutationFn: (data: UpdateCreateRequest) => createUpdate(data),
    onSuccess: () => {
      toast.success(t("messages.created"));
      invalidateAfterWrite(queryClient, orgId);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useEditUpdate() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("updates");

  return useMutation({
    mutationFn: ({ updateId, data }: { updateId: string; data: UpdateEditRequest }) =>
      editUpdate(updateId, data),
    onSuccess: () => {
      toast.success(t("messages.updated"));
      invalidateAfterWrite(queryClient, orgId);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}

export function useDeleteUpdate() {
  const queryClient = useQueryClient();
  const orgId = useChatOrganizationId();
  const t = useTranslations("updates");

  return useMutation({
    mutationFn: (updateId: string) => deleteUpdate(updateId),
    onSuccess: async (_, updateId) => {
      toast.success(t("messages.deleted"));
      // Dropped first, so an open edit screen does not refetch a 404.
      queryClient.removeQueries({ queryKey: UPDATE_KEYS.detail(updateId) });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: UPDATE_KEYS.all }),
        invalidateChannel(queryClient, orgId),
      ]);
    },
    onError: (error: Error) => {
      toast.error(error.message);
    },
  });
}
