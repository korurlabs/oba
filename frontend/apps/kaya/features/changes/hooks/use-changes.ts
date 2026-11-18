"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";

import { invalidateDraft, useVersionMutation } from "@/features/versions/hooks/use-versions";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";
import type { ApiResponse } from "@/types/api";

import { getChange, getFileHistory, listChanges, reviewChange, submitChange } from "../api";
import type {
  ChangeQueryRequest,
  ChangeSubmitRequest,
  FileHistoryResponse,
  ReviewRequest,
} from "../schemas";

export const CHANGES_PAGE_SIZE = 25;

/** Under the versioned keys, so a draft write and an approval reload the change lists. */
const KEYS = {
  list: (organizationId: string, params: ChangeQueryRequest) =>
    [...VERSIONED_KEYS.changes(organizationId), "list", params] as const,
  detail: (organizationId: string, versionId: string) =>
    [...VERSIONED_KEYS.changes(organizationId), "detail", versionId] as const,
  history: (organizationId: string, versionId: string, path: string) =>
    [...VERSIONED_KEYS.changes(organizationId), "history", versionId, path] as const,
};

/** A history page covers this many versions of the owner, the server's page of about 20 renders. */
export const FILE_HISTORY_PAGE = 20;

/** One page of the organization's changes; `enabled` holds the read until its filter is known. */
export function useChanges(params: ChangeQueryRequest, options: { enabled?: boolean } = {}) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, params),
    queryFn: () => listChanges(params),
    enabled: options.enabled ?? true,
  });
}

/** One change for its review screen; the permissions in it are the caller's. */
export function useChange(versionId: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.detail(organization.organizationId, versionId),
    queryFn: () => getChange(versionId),
    enabled: versionId !== "",
  });
}

/**
 * An approval moves the owner's current version and publishes releases, so
 * every versioned read of the organization reloads; a rejection or comment
 * changes only the change itself.
 */
export function useReviewVersion() {
  return useVersionMutation(
    ({ versionId, data }: { versionId: string; data: ReviewRequest }) =>
      reviewChange(versionId, data),
    "Review refused",
    async (queryClient, organizationId, { versionId, data }) => {
      if (data.decision === "APPROVE") {
        await queryClient.invalidateQueries({ queryKey: VERSIONED_KEYS.all(organizationId) });
        return;
      }
      await invalidateDraft(queryClient, organizationId, versionId);
    }
  );
}

export function useSubmitDraft() {
  return useVersionMutation(
    ({ draftId, data }: { draftId: string; data: ChangeSubmitRequest }) =>
      submitChange(draftId, data),
    "Submit refused",
    (queryClient, organizationId, { draftId }) =>
      invalidateDraft(queryClient, organizationId, draftId)
  );
}

/**
 * The page after `response`, or undefined after the last one. The server
 * pages the owner's versions and keeps only those that changed the path, so
 * a page may hold fewer entries than asked, or none, with older pages left.
 */
export function nextFileHistoryPage(
  response: ApiResponse<FileHistoryResponse[]>
): number | undefined {
  const pagination = response.pagination;
  if (!pagination || pagination.last) return undefined;
  return pagination.currentPage + 1;
}

/**
 * The versions that changed a path in the pack of `versionId`,
 * newest first, loaded a page of owner versions at a time; disabled until
 * both are known. It sits under the change keys, so a draft write, a submit
 * and a review reload it with the changes.
 */
export function useFileHistory(versionId: string, path: string) {
  const { organization } = useIdentity();
  return useInfiniteQuery({
    queryKey: KEYS.history(organization.organizationId, versionId, path),
    queryFn: ({ pageParam }: { pageParam: number }) =>
      getFileHistory({ versionId, path, page: pageParam, amount: FILE_HISTORY_PAGE }),
    initialPageParam: 1,
    getNextPageParam: nextFileHistoryPage,
    enabled: versionId !== "" && path !== "",
  });
}
