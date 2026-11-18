"use client";

import { useTranslations } from "next-intl";
import { ExternalLink } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useDocumentTemplate } from "../hooks/use-document-templates";
import { documentTemplateErrorText } from "../lib/document-template-error";
import type { DocumentTemplateDetail } from "../schemas";

export interface DocumentTemplatePreviewDialogProps {
  /** The template to show; null keeps the dialog closed. */
  templateId: string | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * The read-only view of a template that every member may open: its PDF,
 * embedded and with a link that opens it in a new tab. The PDF link is signed for a short time, so
 * the template is loaded again each time the dialog opens.
 */
export function DocumentTemplatePreviewDialog({
  templateId,
  onOpenChange,
}: DocumentTemplatePreviewDialogProps) {
  const t = useTranslations("documentTemplates");
  const tErrors = useTranslations("errors");
  const open = templateId !== null;
  const template = useDocumentTemplate(templateId ?? "", { enabled: open });
  const stored = template.data?.data;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>{stored?.name ?? t("previewTitle")}</DialogTitle>
          <DialogDescription>
            {stored
              ? `${t(`types.${stored.type}`)} · ${t(`languages.${stored.language}`)}`
              : t("previewDescription")}
          </DialogDescription>
        </DialogHeader>
        {template.isError && stored === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={documentTemplateErrorText(template.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void template.refetch()}
          />
        ) : stored === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : (
          <DocumentTemplatePreviewContent template={stored} />
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * The read-only content of a template: the embedded PDF with a new-tab link.
 * `noFilesText` replaces the text shown while no files are stored.
 */
export function DocumentTemplatePreviewContent({
  template,
  noFilesText,
}: {
  template: Pick<DocumentTemplateDetail, "name" | "pdfUrl">;
  noFilesText?: string;
}) {
  const t = useTranslations("documentTemplates");

  if (!template.pdfUrl) {
    return <p className="text-muted-foreground text-sm">{noFilesText ?? t("previewNoFiles")}</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      <iframe
        src={template.pdfUrl}
        title={t("previewFrameTitle", { name: template.name })}
        className="h-[65vh] w-full rounded-md border"
      />
      <a
        href={template.pdfUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="text-primary inline-flex items-center gap-1 self-start text-sm hover:underline"
      >
        <ExternalLink className="size-4" aria-hidden="true" />
        {t("openPdf")}
      </a>
    </div>
  );
}
