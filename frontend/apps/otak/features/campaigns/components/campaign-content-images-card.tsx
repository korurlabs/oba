"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { FileText, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { AttachmentUpload } from "@oba/shared/features/attachments/components";
import { useAttachments, useDeleteAttachment } from "@oba/shared/features/attachments/hooks";
import {
  StorageEntityType,
  type AttachmentResponse,
} from "@oba/shared/features/attachments/schemas";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { campaignErrorText } from "../lib/campaign-error";
import { isCampaignContentImage } from "../lib/campaign-content-images";

/**
 * The images of one content, newest first, with a thumbnail each, to
 * download and post with it. Every member uploads and removes them; a guest
 * only sees them, as the server refuses a guest's writes. The same file twice
 * on one content is refused by the server.
 */
export function CampaignContentImagesCard({ contentId }: { contentId: string }) {
  const t = useTranslations("campaigns.images");
  const tCampaigns = useTranslations("campaigns");
  const tErrors = useTranslations("errors");
  const { formatDateTime } = useDateFormat();
  const isMember = useIsOrganizationMember();
  const attachments = useAttachments(StorageEntityType.CAMPAIGN_CONTENT, contentId);
  const deleteAttachment = useDeleteAttachment();
  const [pendingDelete, setPendingDelete] = useState<AttachmentResponse | null>(null);

  const confirmDelete = () => {
    if (pendingDelete === null) return;
    const attachmentId = pendingDelete.id;
    setPendingDelete(null);
    deleteAttachment.mutate({
      attachmentId,
      entityType: StorageEntityType.CAMPAIGN_CONTENT,
      entityId: contentId,
    });
  };

  const rows = attachments.data?.data ?? [];

  return (
    <section aria-labelledby="campaign-content-images" className="flex flex-col gap-3">
      <h3 id="campaign-content-images" className="text-sm font-medium">
        {t("title")}
      </h3>
      {attachments.isError && attachments.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={campaignErrorText(attachments.error, tCampaigns, "errors.loadHint")}
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
        <ul className="grid gap-3 sm:grid-cols-2" aria-label={t("title")}>
          {rows.map((attachment) => (
            <li key={attachment.id} className="bg-card flex flex-col gap-2 rounded-lg border p-2">
              <a
                href={attachment.url}
                target="_blank"
                rel="noopener noreferrer"
                className="bg-muted flex aspect-video items-center justify-center overflow-hidden rounded-md"
                aria-label={t("open", { name: attachment.fileName })}
              >
                {isCampaignContentImage(attachment) ? (
                  // A signed storage address that expires, so next/image cannot cache it.
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={attachment.url}
                    alt={attachment.fileName}
                    className="h-full w-full object-cover"
                    loading="lazy"
                  />
                ) : (
                  <FileText aria-hidden="true" className="text-muted-foreground size-8" />
                )}
              </a>
              <div className="flex items-start gap-2">
                <div className="min-w-0 flex-1">
                  <a
                    href={attachment.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    download={attachment.fileName}
                    className="block truncate text-sm font-medium hover:underline"
                  >
                    {attachment.fileName}
                  </a>
                  <p className="text-muted-foreground text-xs tabular-nums">
                    {formatDateTime(attachment.createdAt)}
                  </p>
                </div>
                {isMember && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="text-destructive hover:text-destructive shrink-0"
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
        <AttachmentUpload entityType={StorageEntityType.CAMPAIGN_CONTENT} entityId={contentId} />
      )}
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
    </section>
  );
}
