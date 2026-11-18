"use client";

import { useEffect, useMemo, useState } from "react";
import { useFormContext, type Path, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import { ExternalLink } from "lucide-react";
import { FormGrid, ObaForm, SelectField, TextField } from "@oba/shared/features/form";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import {
  useCreateDocumentTemplate,
  useDocumentTemplate,
  useReplaceDocumentTemplateFiles,
  useUpdateDocumentTemplate,
} from "../hooks/use-document-templates";
import {
  documentTemplateErrorText,
  documentTemplateFieldErrors,
} from "../lib/document-template-error";
import {
  createDocumentTemplateFormSchema,
  DOCUMENT_TEMPLATE_LIMITS,
  documentTemplateToFormValues,
  DocumentLanguageSchema,
  DocumentTypeSchema,
  emptyDocumentTemplateFormValues,
  HTML_MEDIA_TYPE,
  PDF_MEDIA_TYPE,
  toDocumentTemplateFiles,
  toDocumentTemplateRequest,
  type DocumentTemplateDetail,
  type DocumentTemplateFormValues,
} from "../schemas";
import { DocumentTemplateFileField } from "./document-template-file-field";

export interface DocumentTemplateFormDialogProps {
  /** The template to edit, or null for a new one. */
  templateId: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Creates a template or edits one; closes once the server has stored it.
 * A template is an HTML file and the PDF rendered from it, uploaded together
 * after the type, name and language are saved. Without files it is saved but
 * not ready, and an edit keeps the stored files unless both are picked again.
 */
export function DocumentTemplateFormDialog({
  templateId,
  open,
  onOpenChange,
}: DocumentTemplateFormDialogProps) {
  const t = useTranslations("documentTemplates");
  const tErrors = useTranslations("errors");
  const tForm = useTranslations("documentTemplates.form");
  const schema = useMemo(() => createDocumentTemplateFormSchema(tForm), [tForm]);
  const createTemplate = useCreateDocumentTemplate();
  const updateTemplate = useUpdateDocumentTemplate();
  const replaceFiles = useReplaceDocumentTemplateFiles();
  const isPending = createTemplate.isPending || updateTemplate.isPending || replaceFiles.isPending;
  const template = useDocumentTemplate(templateId ?? "", {
    enabled: open && templateId !== null,
  });
  const stored = template.data?.data;
  // A new template whose content was stored but whose files were refused:
  // the next save updates it and retries the upload instead of creating it again.
  const [createdId, setCreatedId] = useState<string | null>(null);
  useEffect(() => {
    if (!open) setCreatedId(null);
  }, [open]);
  const initialValues = useMemo(
    () => (stored ? documentTemplateToFormValues(stored) : emptyDocumentTemplateFormValues()),
    [stored]
  );

  const handleSubmit = (
    values: DocumentTemplateFormValues,
    form: UseFormReturn<DocumentTemplateFormValues>
  ) => {
    const onError = (error: unknown) => applyDocumentTemplateFieldErrors(form, error, t);
    const data = toDocumentTemplateRequest(values);
    const files = toDocumentTemplateFiles(values);
    const afterSave = (savedId: string) => {
      if (files === null) {
        onOpenChange(false);
        return;
      }
      replaceFiles.mutate(
        { templateId: savedId, files },
        { onSuccess: () => onOpenChange(false), onError }
      );
    };
    const targetId = templateId ?? createdId;
    if (targetId) {
      updateTemplate.mutate(
        { templateId: targetId, data },
        { onSuccess: () => afterSave(targetId), onError }
      );
    } else {
      createTemplate.mutate(data, {
        onSuccess: (response) => {
          const id = response.data?.id;
          if (id === undefined) {
            onOpenChange(false);
            return;
          }
          setCreatedId(id);
          afterSave(id);
        },
        onError,
      });
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{templateId ? t("editTitle") : t("newTitle")}</DialogTitle>
          <DialogDescription>{t("dialogDescription")}</DialogDescription>
        </DialogHeader>
        {templateId !== null && template.isError && stored === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={documentTemplateErrorText(template.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void template.refetch()}
          />
        ) : templateId !== null && stored === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : (
          // Mounted per open, so a reopened dialog starts from the template, not the last input,
          // and again when a save changed the stored template, so the form and the file
          // pickers show what is stored now.
          open && (
            <ObaForm
              key={templateId ? `${templateId}-${stored?.updatedAt ?? ""}` : "new"}
              schema={schema}
              defaultValues={initialValues}
              onSubmit={handleSubmit}
              className="space-y-6"
            >
              <DocumentTemplateFormFields isPending={isPending} stored={stored} />
              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  disabled={isPending}
                  onClick={() => onOpenChange(false)}
                >
                  {tForm("cancel")}
                </Button>
                <Button type="submit" disabled={isPending}>
                  {isPending ? tForm("saving") : templateId ? tForm("save") : tForm("create")}
                </Button>
              </DialogFooter>
            </ObaForm>
          )
        )}
      </DialogContent>
    </Dialog>
  );
}

/** Puts the fields a refused save or upload names on the form. */
function applyDocumentTemplateFieldErrors(
  form: UseFormReturn<DocumentTemplateFormValues>,
  error: unknown,
  t: (key: string) => string
): void {
  const fields = Object.keys(form.getValues());
  for (const [field, message] of Object.entries(documentTemplateFieldErrors(error, t))) {
    if (fields.includes(field)) {
      form.setError(field as Path<DocumentTemplateFormValues>, { type: "server", message });
    }
  }
}

/**
 * The content fields of a template: type, language and name, then the HTML
 * and PDF file. `filesHint` replaces the hint above the file pickers of new content.
 */
export function DocumentTemplateFormFields({
  isPending,
  stored,
  filesHint,
}: {
  isPending: boolean;
  stored: Pick<DocumentTemplateDetail, "ready" | "pdfUrl"> | undefined;
  filesHint?: string;
}) {
  const t = useTranslations("documentTemplates");
  const { control } = useFormContext<DocumentTemplateFormValues>();

  const typeOptions = useMemo(
    () => DocumentTypeSchema.options.map((value) => ({ value, label: t(`types.${value}`) })),
    [t]
  );
  const languageOptions = useMemo(
    () =>
      DocumentLanguageSchema.options.map((value) => ({ value, label: t(`languages.${value}`) })),
    [t]
  );

  return (
    <FormGrid>
      <SelectField
        name="type"
        label={t("form.type")}
        control={control}
        options={typeOptions}
        required
        disabled={isPending}
      />
      <SelectField
        name="language"
        label={t("form.language")}
        control={control}
        options={languageOptions}
        required
        disabled={isPending}
      />
      <div className="md:col-span-2">
        <TextField
          name="name"
          label={t("form.name")}
          placeholder={t("form.namePlaceholder")}
          control={control}
          required
          maxLength={DOCUMENT_TEMPLATE_LIMITS.nameMax}
          disabled={isPending}
        />
      </div>
      <p className="text-muted-foreground text-sm md:col-span-2">
        {stored?.ready ? t("form.filesReplaceHint") : (filesHint ?? t("form.filesHint"))}
      </p>
      <DocumentTemplateFileField
        name="htmlFile"
        label={t("form.htmlFile")}
        accept={`.html,.htm,${HTML_MEDIA_TYPE}`}
        control={control}
        disabled={isPending}
      />
      <DocumentTemplateFileField
        name="pdfFile"
        label={t("form.pdfFile")}
        accept={`.pdf,${PDF_MEDIA_TYPE}`}
        control={control}
        disabled={isPending}
      />
      {stored?.pdfUrl && (
        <div className="md:col-span-2">
          <a
            href={stored.pdfUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary inline-flex items-center gap-1 text-sm hover:underline"
          >
            <ExternalLink className="size-4" aria-hidden="true" />
            {t("form.openCurrentPdf")}
          </a>
        </div>
      )}
    </FormGrid>
  );
}
