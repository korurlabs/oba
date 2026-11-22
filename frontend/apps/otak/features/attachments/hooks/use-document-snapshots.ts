"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { ATTACHMENT_KEYS } from "@oba/shared/features/attachments/hooks";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";
import { DOCUMENT_KEYS } from "@/features/documents/hooks/query-keys";
import { RELATION_KEYS } from "@/features/relations/hooks/query-keys";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { attachDocumentSnapshot } from "../api";
import { documentSnapshotErrorText } from "../lib/document-snapshots";

/**
 * The lists a change to a mail's files makes stale: the mail's own list,
 * every relation's attachment list, which shows the files of its mails, and,
 * for a snapshot, the documents with their snapshot counts.
 */
export function useInvalidateMailAttachments() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return (mailId: string, { documents = false }: { documents?: boolean } = {}) => {
    void queryClient.invalidateQueries({
      queryKey: ATTACHMENT_KEYS.list(StorageEntityType.MAIL, mailId),
    });
    if (organizationId) {
      void queryClient.invalidateQueries({
        queryKey: RELATION_KEYS.attachmentLists(organizationId),
      });
      if (documents) {
        void queryClient.invalidateQueries({ queryKey: DOCUMENT_KEYS.all(organizationId) });
      }
    }
  };
}

/** Attaches a copy of a document's PDF to a mail and refreshes the lists that show it. */
export function useAttachDocumentSnapshot() {
  const invalidate = useInvalidateMailAttachments();
  const t = useTranslations("outreaches.mailAttachments");

  return useMutation({
    mutationFn: ({ documentId, mailId }: { documentId: string; mailId: string }) =>
      attachDocumentSnapshot(documentId, {
        entityType: StorageEntityType.MAIL,
        entityId: mailId,
      }),
    onSuccess: (_result, { mailId }) => {
      toast.success(t("toast.added"));
      invalidate(mailId, { documents: true });
    },
    onError: (error: unknown) => {
      toast.error(documentSnapshotErrorText(error, t, "toast.addError"));
    },
  });
}
