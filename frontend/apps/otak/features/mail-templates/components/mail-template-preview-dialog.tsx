"use client";

import { useTranslations } from "next-intl";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useMailTemplate } from "../hooks/use-mail-templates";
import { mailTemplateErrorText } from "../lib/mail-template-error";

export interface MailTemplatePreviewDialogProps {
  /** The mail template to show; null keeps the dialog closed. */
  mailTemplateId: string | null;
  onOpenChange: (open: boolean) => void;
}

/** The read-only view of a mail template that every member may open: its subject and body. */
export function MailTemplatePreviewDialog({
  mailTemplateId,
  onOpenChange,
}: MailTemplatePreviewDialogProps) {
  const t = useTranslations("mailTemplates");
  const tTemplates = useTranslations("documentTemplates");
  const tErrors = useTranslations("errors");
  const open = mailTemplateId !== null;
  const template = useMailTemplate(mailTemplateId ?? "", { enabled: open });
  const stored = template.data?.data;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="break-words">{stored?.name ?? t("previewTitle")}</DialogTitle>
          <DialogDescription>
            {stored ? tTemplates(`languages.${stored.language}`) : t("loadingOne")}
          </DialogDescription>
        </DialogHeader>
        {template.isError && stored === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={mailTemplateErrorText(template.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void template.refetch()}
          />
        ) : stored === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : (
          <dl className="flex flex-col gap-4 text-sm">
            <div className="flex flex-col gap-1">
              <dt className="text-muted-foreground">{t("form.subject")}</dt>
              <dd className="font-medium break-words">{stored.subject}</dd>
            </div>
            <div className="flex flex-col gap-1">
              <dt className="text-muted-foreground">{t("form.body")}</dt>
              <dd className="max-h-[60vh] overflow-y-auto break-words whitespace-pre-wrap">
                {stored.body}
              </dd>
            </div>
          </dl>
        )}
      </DialogContent>
    </Dialog>
  );
}
