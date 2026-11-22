"use client";

import { useEffect, useMemo, useState } from "react";
import { useFormContext, useWatch, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { FormGrid, ObaForm, SelectField } from "@oba/shared/features/form";
import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { documentTemplateErrorText } from "@/features/document-templates/lib/document-template-error";
import type { DocumentTemplateListItem } from "@/features/document-templates/schemas";
import { applyDocumentFieldErrors } from "@/features/documents/components/apply-document-field-errors";
import { DocumentDetailFields } from "@/features/documents/components/document-form-fields";
import { useCreateDocument } from "@/features/documents/hooks/use-documents";
import { useLegalEntities } from "@/features/documents/hooks/use-legal-entities";
import { useTemplateOptions } from "@/features/documents/hooks/use-template-options";
import { documentErrorText } from "@/features/documents/lib/document-error";
import {
  emptyDocumentCreateFormValues,
  toDocumentCreateRequest,
  type LegalEntityListItem,
} from "@/features/documents/schemas";
import { useAttachDocumentSnapshot } from "../hooks/use-document-snapshots";
import { attachableTemplates } from "../lib/document-snapshots";
import {
  createDocumentFromTemplateFormSchema,
  type DocumentFromTemplateFormValues,
} from "../schemas";

export interface DocumentFromTemplateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mailId: string;
  /** The mail's relation, which owns the new document. */
  relationId: string;
}

/**
 * Makes a document of the mail's relation from a template and attaches a
 * copy of its PDF to the mail, so a mail can carry a document the relation
 * did not have yet. The document is stored first: when the attach is
 * refused, it stays with the relation and can be picked again.
 */
export function DocumentFromTemplateDialog({
  open,
  onOpenChange,
  mailId,
  relationId,
}: DocumentFromTemplateDialogProps) {
  const t = useTranslations("outreaches.mailAttachments.fromTemplate");
  const tDocuments = useTranslations("documents");
  const tForm = useTranslations("documents.form");
  const tErrors = useTranslations("errors");
  const schema = useMemo(() => createDocumentFromTemplateFormSchema(tForm, t), [tForm, t]);
  const legalEntities = useLegalEntities();
  const createDocument = useCreateDocument();
  const attach = useAttachDocumentSnapshot();
  const entities = legalEntities.data?.data;
  const isPending = createDocument.isPending || attach.isPending;

  const handleSubmit = (
    values: DocumentFromTemplateFormValues,
    form: UseFormReturn<DocumentFromTemplateFormValues>
  ) => {
    createDocument.mutate(toDocumentCreateRequest(values), {
      onSuccess: (response) => {
        const document = response.data;
        // The document exists from here on, so the dialog closes whatever the
        // attach answers; a second submit would make a duplicate.
        onOpenChange(false);
        if (!document?.ready) {
          toast.error(t("notReady"));
          return;
        }
        attach.mutate({ documentId: document.id, mailId });
      },
      onError: (error) => applyDocumentFieldErrors(form, error, tDocuments),
    });
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>
        {legalEntities.isError && entities === undefined ? (
          <LoadError
            title={tDocuments("legalEntitiesLoadError")}
            message={documentErrorText(legalEntities.error, tDocuments, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void legalEntities.refetch()}
          />
        ) : entities === undefined ? (
          <TableLoading label={tDocuments("loadingLegalEntities")} />
        ) : entities.length === 0 ? (
          <p className="text-muted-foreground text-sm">{tDocuments("noLegalEntities")}</p>
        ) : (
          // Mounted per open, so a reopened dialog starts empty, not from the last input.
          open && (
            <ObaForm
              schema={schema}
              defaultValues={emptyDocumentCreateFormValues(
                relationId,
                entities.length === 1 ? (entities[0]?.id ?? null) : null
              )}
              onSubmit={handleSubmit}
              className="space-y-6"
            >
              <DocumentFromTemplateFields legalEntities={entities} isPending={isPending} />
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
                  {isPending ? t("creating") : t("create")}
                </Button>
              </DialogFooter>
            </ObaForm>
          )
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * The template and the shared detail fields. Picking a template takes over
 * its type, its language, its legal entity when it is bound to one, and its
 * name while the title is still empty; a template of another legal entity is
 * dropped when the legal entity changes, as the server refuses it.
 */
function DocumentFromTemplateFields({
  legalEntities,
  isPending,
}: {
  legalEntities: LegalEntityListItem[];
  isPending: boolean;
}) {
  const t = useTranslations("outreaches.mailAttachments.fromTemplate");
  const tForm = useTranslations("documents.form");
  const tTemplates = useTranslations("documentTemplates");
  const { control, getValues, setValue } = useFormContext<DocumentFromTemplateFormValues>();
  const legalEntityId = useWatch({ control, name: "legalEntityId" });
  const templateId = useWatch({ control, name: "templateId" });
  const [picked, setPicked] = useState<DocumentTemplateListItem | null>(null);
  const [search, setSearch] = useState("");
  const templates = useTemplateOptions(legalEntityId || null, picked);
  const onSearchChange = (next: string) => {
    setSearch(next);
    templates.onSearchChange(next);
  };

  const ready = useMemo(() => attachableTemplates(templates.rows), [templates.rows]);
  const options = useMemo(() => {
    const readyIds = new Set(ready.map((template) => template.id));
    return templates.options.filter((option) => readyIds.has(option.value));
  }, [ready, templates.options]);

  useEffect(() => {
    if (templateId === "") {
      setPicked(null);
      return;
    }
    if (picked?.id === templateId) return;
    const template = ready.find((row) => row.id === templateId);
    if (!template) return;
    setPicked(template);
    setValue("type", template.type);
    setValue("language", template.language);
    if (template.legalEntityId && getValues("legalEntityId") === "") {
      setValue("legalEntityId", template.legalEntityId, { shouldValidate: true });
    }
    if (getValues("title").trim() === "") {
      setValue("title", template.name, { shouldValidate: true });
    }
  }, [templateId, picked, ready, getValues, setValue]);

  useEffect(() => {
    if (picked?.legalEntityId && legalEntityId && picked.legalEntityId !== legalEntityId) {
      setValue("templateId", "");
    }
  }, [legalEntityId, picked, setValue]);

  const noTemplates =
    templates.error === null && !templates.isLoading && options.length === 0 && search === "";

  return (
    <FormGrid>
      <div className="md:col-span-2">
        <SelectField
          name="templateId"
          label={tForm("template")}
          description={t("templateHint")}
          placeholder={t("templatePlaceholder")}
          searchPlaceholder={tForm("templateSearchPlaceholder")}
          control={control}
          options={options}
          onSearchChange={onSearchChange}
          isLoading={templates.isLoading}
          required
          disabled={isPending}
        />
        {templates.error !== null && (
          <p className="text-destructive mt-1 text-sm" role="alert">
            {documentTemplateErrorText(templates.error, tTemplates, "errors.loadHint")}
          </p>
        )}
        {noTemplates && (
          <p className="text-muted-foreground mt-1 text-sm" role="status">
            {t("noTemplates")}
          </p>
        )}
      </div>
      <DocumentDetailFields legalEntities={legalEntities} isPending={isPending} />
    </FormGrid>
  );
}
