"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { ExternalLink, FileText, FolderOpen, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { AttachmentUpload } from "@oba/shared/features/attachments/components";
import { useDeleteAttachment } from "@oba/shared/features/attachments/hooks";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { RELATION_KEYS } from "../hooks/query-keys";
import { useRelationAttachments } from "../hooks/use-relations";
import {
  canDeleteRelationAttachment,
  relationAttachmentSourceText,
} from "../lib/relation-attachments";
import { relationErrorText } from "../lib/relation-error";
import type { RelationAttachment } from "../schemas";

/**
 * The files of a relation and of its mails, newest first, such as a signed
 * NDA, the customer's RFP or the PDF a mail carried. A snapshot of a document
 * opens that document. Every member uploads files to the relation and
 * removes them; a guest only lists them, as the server refuses a guest's
 * writes. A mail's file is changed on the mail.
 */
export function RelationAttachmentsCard({ relationId }: { relationId: string }) {
  const t = useTranslations("relations.attachments");
  const tRelations = useTranslations("relations");
  const tErrors = useTranslations("errors");
  const { formatDateTime } = useDateFormat();
  const isMember = useIsOrganizationMember();
  const organizationId = useOrganizationId();
  const queryClient = useQueryClient();
  const attachments = useRelationAttachments(relationId);
  const deleteAttachment = useDeleteAttachment();
  const [pendingDelete, setPendingDelete] = useState<RelationAttachment | null>(null);

  const refresh = () => {
    if (organizationId) {
      void queryClient.invalidateQueries({
        queryKey: RELATION_KEYS.attachments(organizationId, relationId),
      });
    }
  };

  const confirmDelete = () => {
    if (pendingDelete === null) return;
    const attachmentId = pendingDelete.id;
    setPendingDelete(null);
    deleteAttachment.mutate(
      { attachmentId, entityType: StorageEntityType.RELATION, entityId: relationId },
      { onSuccess: refresh }
    );
  };

  const rows = attachments.data?.data ?? [];

  return (
    <Card role="region" aria-labelledby="attachments" className="gap-4">
      <CardHeader className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="attachments" className="text-lg leading-none">
          {t("title")}
        </h2>
        <Button asChild variant="outline" size="sm">
          <Link href={{ pathname: "/documents", query: { relation: relationId } }}>
            <FolderOpen aria-hidden="true" />
            {t("documentsLink")}
          </Link>
        </Button>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {attachments.isError && attachments.data === undefined ? (
          <LoadError
            title={t("loadError")}
            message={relationErrorText(attachments.error, tRelations, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void attachments.refetch()}
          />
        ) : attachments.data === undefined ? (
          <TableLoading label={t("loading")} />
        ) : rows.length === 0 ? (
          <p className="text-muted-foreground text-sm" role="status">
            {t("empty")}
          </p>
        ) : (
          <ul className="flex flex-col gap-2" aria-label={t("title")}>
            {rows.map((attachment) => (
              <li
                key={attachment.id}
                className="bg-card flex flex-wrap items-center gap-3 rounded-lg border p-2"
              >
                <FileText aria-hidden="true" className="text-muted-foreground size-5 shrink-0" />
                <div className="min-w-0 flex-1">
                  <a
                    href={attachment.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block truncate text-sm font-medium hover:underline"
                  >
                    {attachment.fileName}
                  </a>
                  <p className="text-muted-foreground text-xs">
                    {relationAttachmentSourceText(attachment, t)}
                    {" · "}
                    <span className="tabular-nums">{formatDateTime(attachment.createdAt)}</span>
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  {attachment.documentId !== null && (
                    <Button asChild variant="ghost" size="sm">
                      <Link href={`/documents/${attachment.documentId}`}>
                        <ExternalLink aria-hidden="true" />
                        {t("openDocument")}
                      </Link>
                    </Button>
                  )}
                  {canDeleteRelationAttachment(attachment, isMember) && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="text-destructive hover:text-destructive"
                      aria-label={t("delete", { name: attachment.fileName })}
                      disabled={deleteAttachment.isPending}
                      onClick={() => setPendingDelete(attachment)}
                    >
                      <Trash2 aria-hidden="true" />
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
        {isMember && (
          <AttachmentUpload
            entityType={StorageEntityType.RELATION}
            entityId={relationId}
            onUploadSuccess={refresh}
          />
        )}
      </CardContent>
      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { name: pendingDelete?.fileName ?? "" })}
        confirmLabel={t("deleteAction")}
        cancelLabel={t("cancel")}
        destructive
        onConfirm={confirmDelete}
      />
    </Card>
  );
}
