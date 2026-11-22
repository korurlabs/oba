"use client";

import { useId } from "react";
import { useTranslations } from "next-intl";
import { FileText, Link2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { AttachmentUpload } from "@oba/shared/features/attachments/components";
import { StorageEntityType } from "@oba/shared/features/attachments/schemas";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { usePageAttachments } from "../hooks/use-page-attachments";
import { pageErrorText } from "../lib/page-error";
import { markdownLink } from "../schemas/page-form";

/**
 * The files attached to a page, newest first, with the upload area and a way
 * to put a link to a file into the body. A page's files are kept as a record,
 * so none can be removed here.
 */
export function PageAttachmentsCard({
  pageId,
  insertTargetLabel,
  onInsertLink,
}: {
  pageId: string;
  /** The language whose body a link goes into, named on the insert buttons. */
  insertTargetLabel: string;
  onInsertLink: (markdown: string) => void;
}) {
  const t = useTranslations("pageForm.attachments");
  const tPages = useTranslations("pages");
  const headingId = useId();
  const { formatDateTime } = useDateFormat();
  const attachments = usePageAttachments(pageId);
  const rows = [...(attachments.data?.data ?? [])].sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt)
  );

  return (
    <Card role="region" aria-labelledby={headingId} className="gap-4">
      <CardHeader className="flex flex-col gap-1">
        <h2 id={headingId} className="text-lg leading-none">
          {t("title")}
        </h2>
        <p className="text-muted-foreground text-sm">{t("description")}</p>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {attachments.isError && attachments.data === undefined ? (
          <LoadError
            title={t("loadError")}
            message={pageErrorText(attachments.error, tPages, "view.loadError")}
            retryLabel={t("retry")}
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
                  <p className="text-muted-foreground text-xs tabular-nums">
                    {formatDateTime(attachment.createdAt)}
                  </p>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  aria-label={t("insertLinkFor", {
                    name: attachment.fileName,
                    language: insertTargetLabel,
                  })}
                  onClick={() => onInsertLink(markdownLink(attachment.fileName, attachment.url))}
                >
                  <Link2 aria-hidden="true" />
                  {t("insertLink")}
                </Button>
              </li>
            ))}
          </ul>
        )}
        <AttachmentUpload entityType={StorageEntityType.KALKAN_PAGE} entityId={pageId} />
      </CardContent>
    </Card>
  );
}
