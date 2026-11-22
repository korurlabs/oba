"use client";

import { useAttachments, useUploadAttachments } from "@oba/shared/features/attachments/hooks";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";

/**
 * The files attached to a page, through the shared `/storage/attachments`
 * endpoints. A page's attachments are kept as a record: the server refuses
 * their delete (`KLK_SEC_002`), so Kalkan offers no delete hook.
 */
export function usePageAttachments(pageId: string | undefined) {
  return useAttachments(StorageEntityType.KALKAN_PAGE, pageId);
}

/**
 * Uploads files to a page. Writing needs a MEMBER or above, and EDIT on the
 * page's root. The shared hook shows its own toasts and refreshes the list.
 */
export function useUploadPageAttachments(options?: Parameters<typeof useUploadAttachments>[0]) {
  const upload = useUploadAttachments(options);
  return {
    ...upload,
    mutate: (variables: { pageId: string; files: File[] }) =>
      upload.mutate({
        entityType: StorageEntityType.KALKAN_PAGE,
        entityId: variables.pageId,
        files: variables.files,
      }),
    mutateAsync: (variables: { pageId: string; files: File[] }) =>
      upload.mutateAsync({
        entityType: StorageEntityType.KALKAN_PAGE,
        entityId: variables.pageId,
        files: variables.files,
      }),
  };
}
