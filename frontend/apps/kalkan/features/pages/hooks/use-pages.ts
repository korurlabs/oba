"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import {
  createPage,
  deletePage,
  getPage,
  getPageRevision,
  listPageRevisions,
  listPages,
  listRecentPages,
  listTrash,
  movePage,
  restorePage,
  updatePage,
} from "../api";
import { pageErrorText } from "../lib/page-error";
import {
  RECENT_LIMITS,
  type PageCreateRequest,
  type PageMoveRequest,
  type PageUpdateRequest,
} from "../schemas";
import { PAGE_KEYS } from "./query-keys";

export { PAGE_KEYS };

// ── Queries ─────────────────────────────────────────────────────────

/**
 * The organization's page tree, flat, parents before children; waits for the
 * organization. The sidebar, the sections and the overview all read it, so
 * they share one request.
 */
export function usePageTree() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: PAGE_KEYS.tree(organizationId ?? ""),
    queryFn: listPages,
    enabled: !!organizationId,
  });
}

/** One page with its translations; waits for the organization and an id. */
export function usePage(pageId: string | undefined) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: PAGE_KEYS.detail(organizationId ?? "", pageId ?? ""),
    queryFn: () => getPage(pageId!),
    enabled: !!organizationId && !!pageId,
  });
}

/** The revisions of a page, newest first. */
export function usePageRevisions(pageId: string | undefined) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: PAGE_KEYS.revisions(organizationId ?? "", pageId ?? ""),
    queryFn: () => listPageRevisions(pageId!),
    enabled: !!organizationId && !!pageId,
  });
}

/** One revision of a page with its body. */
export function usePageRevision(pageId: string | undefined, revisionId: string | undefined) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: PAGE_KEYS.revision(organizationId ?? "", pageId ?? "", revisionId ?? ""),
    queryFn: () => getPageRevision(pageId!, revisionId!),
    enabled: !!organizationId && !!pageId && !!revisionId,
  });
}

/**
 * The deleted pages the caller can restore. The server refuses a GUEST, so a
 * caller passes `enabled: false` for one instead of showing a refusal.
 */
export function useTrash({ enabled = true }: { enabled?: boolean } = {}) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: PAGE_KEYS.trash(organizationId ?? ""),
    queryFn: listTrash,
    enabled: enabled && !!organizationId,
  });
}

/** The most recently changed pages the caller can see; `limit` is 1 to 100. */
export function useRecentPages(limit: number = RECENT_LIMITS.default) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: PAGE_KEYS.recent(organizationId ?? "", limit),
    queryFn: () => listRecentPages(limit),
    enabled: !!organizationId,
  });
}

// ── Mutations ───────────────────────────────────────────────────────

/**
 * A page change refreshes every page query of the organization: the tree,
 * the details, the history, the trash and the recent list all follow from
 * one write.
 */
export function useInvalidatePages() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: PAGE_KEYS.all(organizationId) });
    }
  };
}

export function useCreatePage() {
  const invalidate = useInvalidatePages();
  const t = useTranslations("pages");

  return useMutation({
    mutationFn: (data: PageCreateRequest) => createPage(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(pageErrorText(error, t, "toast.createError"));
    },
  });
}

export function useUpdatePage() {
  const invalidate = useInvalidatePages();
  const t = useTranslations("pages");

  return useMutation({
    mutationFn: ({ pageId, data }: { pageId: string; data: PageUpdateRequest }) =>
      updatePage(pageId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(pageErrorText(error, t, "toast.updateError"));
    },
  });
}

export function useMovePage() {
  const invalidate = useInvalidatePages();
  const t = useTranslations("pages");

  return useMutation({
    mutationFn: ({ pageId, data }: { pageId: string; data: PageMoveRequest }) =>
      movePage(pageId, data),
    onSuccess: () => {
      toast.success(t("toast.moved"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(pageErrorText(error, t, "toast.moveError"));
    },
  });
}

/**
 * Deleting a page drops its cached detail first, so the page view does not
 * refetch a page that now answers 404.
 */
export function useDeletePage() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("pages");

  return useMutation({
    mutationFn: (pageId: string) => deletePage(pageId),
    onSuccess: (_result, pageId) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        queryClient.removeQueries({ queryKey: PAGE_KEYS.detail(organizationId, pageId) });
        void queryClient.invalidateQueries({ queryKey: PAGE_KEYS.all(organizationId) });
      }
    },
    onError: (error: unknown) => {
      toast.error(pageErrorText(error, t, "toast.deleteError"));
    },
  });
}

export function useRestorePage() {
  const invalidate = useInvalidatePages();
  const t = useTranslations("pages");

  return useMutation({
    mutationFn: (pageId: string) => restorePage(pageId),
    onSuccess: () => {
      toast.success(t("toast.restored"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(pageErrorText(error, t, "toast.restoreError"));
    },
  });
}
