"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { deleteAttachment, listAttachments, uploadAttachments } from "../api";
import type { StorageEntityType } from "../schemas";

const KEYS = {
  all: ["attachments"] as const,
  list: (entityType: StorageEntityType, entityId: string) =>
    [...KEYS.all, "list", entityType, entityId] as const,
};

export { KEYS as ATTACHMENT_KEYS };

export function useAttachments(
  entityType: StorageEntityType,
  entityId: string | undefined,
) {
  return useQuery({
    queryKey: KEYS.list(entityType, entityId!),
    queryFn: () => listAttachments(entityType, entityId!),
    enabled: !!entityId,
    // The attachment collection for an entity is routinely mutated by
    // writers that live outside this React tree, so a cached page of it
    // can be wrong through no fault of any local mutation:
    //   - the kaizen CLI (`kaizen ... --attach`) uploads server-side,
    //   - the ticket sheet's orphan GC hard-deletes through the raw
    //     `deleteAttachment` API function (see lib/orphan-gc.ts), and
    //   - `commitPendingImages` uploads description images the same way.
    // None of those touch this query key. The global defaults are
    // staleTime 60s with refetchOnMount left at its default (see
    // providers/query-client.ts), so remounting a consumer within the
    // stale window would serve a list that still contains a deleted blob
    // -- rendering a row whose download 404s and whose delete errors.
    // "always" makes every mount re-read the server's truth.
    refetchOnMount: "always",
  });
}

/** Options for {@link useUploadAttachments}. */
export interface UseUploadAttachmentsOptions {
  /**
   * Predicate consulted before the default error toast fires. Return
   * `true` to suppress the toast for a given error (the caller handles it
   * itself — e.g. treating a benign "already attached" backend response as
   * a success). Returning `false`/`undefined` keeps the default toast.
   *
   * The default toast is the standard surface for upload errors; this hook
   * only lets a caller opt a *specific* error out of it. Per
   * .claude/rules/10-no-silent-failures.md the caller that suppresses the
   * toast must still handle the error meaningfully.
   */
  suppressErrorToast?: (error: Error) => boolean;
}

export function useUploadAttachments(options?: UseUploadAttachmentsOptions) {
  const queryClient = useQueryClient();
  const t = useTranslations("attachments");

  return useMutation({
    mutationFn: ({
      entityType,
      entityId,
      files,
    }: {
      entityType: StorageEntityType;
      entityId: string;
      files: File[];
      /**
       * Set when the upload is one request of a larger user action that
       * shows its own single result toast (a ticket created with staged
       * files). Only the success toast is skipped; the error toast and the
       * list invalidation still run.
       */
      silentSuccess?: boolean;
    }) => uploadAttachments(entityType, entityId, files),
    onSuccess: (_data, variables) => {
      if (!variables.silentSuccess) toast.success(t("messages.uploadSuccess"));
      queryClient.invalidateQueries({
        queryKey: KEYS.list(variables.entityType, variables.entityId),
      });
    },
    onError: (error: Error) => {
      if (options?.suppressErrorToast?.(error)) return;
      toast.error(error.message || t("messages.uploadError"));
    },
  });
}

export function useDeleteAttachment() {
  const queryClient = useQueryClient();
  const t = useTranslations("attachments");

  return useMutation({
    mutationFn: ({
      attachmentId,
    }: {
      attachmentId: string;
      entityType: StorageEntityType;
      entityId: string;
    }) => deleteAttachment(attachmentId),
    onSuccess: (_data, variables) => {
      toast.success(t("messages.deleteSuccess"));
      queryClient.invalidateQueries({
        queryKey: KEYS.list(variables.entityType, variables.entityId),
      });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("messages.deleteError"));
    },
  });
}
