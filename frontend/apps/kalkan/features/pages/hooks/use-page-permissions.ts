"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { listPagePermissions, replacePagePermissions } from "../api";
import { pageErrorText } from "../lib/page-error";
import type { PagePermissionRequest } from "../schemas";
import { PAGE_KEYS } from "./query-keys";

/**
 * The live grants of a root page. Only a MANAGE holder may read them, so a
 * caller passes `enabled: false` for anyone else instead of showing a refusal.
 */
export function usePagePermissions(
  rootPageId: string | undefined,
  { enabled = true }: { enabled?: boolean } = {}
) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: PAGE_KEYS.permissions(organizationId ?? "", rootPageId ?? ""),
    queryFn: () => listPagePermissions(rootPageId!),
    enabled: enabled && !!organizationId && !!rootPageId,
  });
}

/**
 * Replaces every grant of a root page. The levels decide what the tree shows
 * to whom, so the whole page cache of the organization is refreshed, the
 * permission list with it.
 */
export function useReplacePagePermissions() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("pages");

  return useMutation({
    mutationFn: ({ rootPageId, grants }: { rootPageId: string; grants: PagePermissionRequest[] }) =>
      replacePagePermissions(rootPageId, grants),
    onSuccess: () => {
      toast.success(t("toast.permissionsSaved"));
      if (organizationId) {
        void queryClient.invalidateQueries({ queryKey: PAGE_KEYS.all(organizationId) });
      }
    },
    onError: (error: unknown) => {
      toast.error(pageErrorText(error, t, "toast.permissionsError"));
    },
  });
}
