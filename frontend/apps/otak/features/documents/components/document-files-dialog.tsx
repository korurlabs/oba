"use client";

import { useMemo } from "react";
import { useFormContext, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import { FormGrid, ObaForm } from "@oba/shared/features/form";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { DocumentTemplateFileField } from "@/features/document-templates/components/document-template-file-field";
import { HTML_MEDIA_TYPE, PDF_MEDIA_TYPE } from "@/features/document-templates/schemas";
import { useReplaceDocumentFiles } from "../hooks/use-documents";
import { createDocumentFilesFormSchema, type DocumentFilesFormValues } from "../schemas";
import { applyDocumentFieldErrors } from "./apply-document-field-errors";

export interface DocumentFilesDialogProps {
  documentId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const EMPTY_FILES: DocumentFilesFormValues = { htmlFile: null, pdfFile: null };

/**
 * Replaces both files of a document with an HTML file and the PDF made from
 * it, uploaded together. PDF copies already sent with mails keep what they
 * were; the CLI does the same with a rendered PDF.
 */
export function DocumentFilesDialog({ documentId, open, onOpenChange }: DocumentFilesDialogProps) {
  const t = useTranslations("documents");
  const tForm = useTranslations("documents.form");
  const schema = useMemo(() => createDocumentFilesFormSchema(tForm), [tForm]);
  const replaceFiles = useReplaceDocumentFiles();

  const handleSubmit = (
    values: DocumentFilesFormValues,
    form: UseFormReturn<DocumentFilesFormValues>
  ) => {
    if (!values.htmlFile || !values.pdfFile) return;
    replaceFiles.mutate(
      { documentId, files: { html: values.htmlFile, pdf: values.pdfFile } },
      {
        onSuccess: () => onOpenChange(false),
        onError: (error) => applyDocumentFieldErrors(form, error, t),
      }
    );
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !replaceFiles.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{t("filesTitle")}</DialogTitle>
          <DialogDescription>{t("filesDescription")}</DialogDescription>
        </DialogHeader>
        {/* Mounted per open, so a reopened dialog has no picked files. */}
        {open && (
          <ObaForm
            schema={schema}
            defaultValues={EMPTY_FILES}
            onSubmit={handleSubmit}
            className="space-y-6"
          >
            <DocumentFileFields isPending={replaceFiles.isPending} />
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                disabled={replaceFiles.isPending}
                onClick={() => onOpenChange(false)}
              >
                {tForm("cancel")}
              </Button>
              <Button type="submit" disabled={replaceFiles.isPending}>
                {replaceFiles.isPending ? tForm("uploading") : tForm("upload")}
              </Button>
            </DialogFooter>
          </ObaForm>
        )}
      </DialogContent>
    </Dialog>
  );
}

function DocumentFileFields({ isPending }: { isPending: boolean }) {
  const t = useTranslations("documents.form");
  const { control } = useFormContext<DocumentFilesFormValues>();
  return (
    <FormGrid>
      <DocumentTemplateFileField
        name="htmlFile"
        label={t("htmlFile")}
        accept={`.html,.htm,${HTML_MEDIA_TYPE}`}
        control={control}
        disabled={isPending}
      />
      <DocumentTemplateFileField
        name="pdfFile"
        label={t("pdfFile")}
        accept={`.pdf,${PDF_MEDIA_TYPE}`}
        control={control}
        disabled={isPending}
      />
    </FormGrid>
  );
}
