"use client";

import { useEffect, useMemo, useState } from "react";
import { useFormContext, useWatch, type UseFormReturn } from "react-hook-form";
import { useTranslations } from "next-intl";
import { FormGrid, ObaForm, SelectField } from "@oba/shared/features/form";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
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
import { documentTemplateErrorText } from "@/features/document-templates/lib/document-template-error";
import type { DocumentTemplateListItem } from "@/features/document-templates/schemas";
import { relationErrorText } from "@/features/relations/lib/relation-error";
import { useCreateDocument } from "../hooks/use-documents";
import { useLegalEntities } from "../hooks/use-legal-entities";
import { useRelationOptions } from "../hooks/use-relation-options";
import { useTemplateOptions } from "../hooks/use-template-options";
import { documentErrorText } from "../lib/document-error";
import {
  createDocumentCreateFormSchema,
  emptyDocumentCreateFormValues,
  toDocumentCreateRequest,
  type DocumentCreateFormValues,
  type LegalEntityListItem,
} from "../schemas";
import { applyDocumentFieldErrors } from "./apply-document-field-errors";
import { DocumentDetailFields } from "./document-form-fields";

export interface DocumentCreateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The relation to preselect, such as the one the list is narrowed to. */
  relationId: string | null;
}

/**
 * Creates a document of a relation, issued by a legal entity, optionally
 * from a template whose files the server copies. Its content is edited
 * afterwards with the CLI, so the dialog opens the new document's page once
 * the server has stored it.
 */
export function DocumentCreateDialog({
  open,
  onOpenChange,
  relationId,
}: DocumentCreateDialogProps) {
  const t = useTranslations("documents");
  const tErrors = useTranslations("errors");
  const tForm = useTranslations("documents.form");
  const router = useRouter();
  const schema = useMemo(() => createDocumentCreateFormSchema(tForm), [tForm]);
  const legalEntities = useLegalEntities();
  const createDocument = useCreateDocument();
  const entities = legalEntities.data?.data;

  const handleSubmit = (
    values: DocumentCreateFormValues,
    form: UseFormReturn<DocumentCreateFormValues>
  ) => {
    createDocument.mutate(toDocumentCreateRequest(values), {
      onSuccess: (response) => {
        onOpenChange(false);
        const id = response.data?.id;
        if (id !== undefined) router.push(`/documents/${id}`);
      },
      onError: (error) => applyDocumentFieldErrors(form, error, t),
    });
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !createDocument.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("newTitle")}</DialogTitle>
          <DialogDescription>{t("newDescription")}</DialogDescription>
        </DialogHeader>
        {legalEntities.isError && entities === undefined ? (
          <LoadError
            title={t("legalEntitiesLoadError")}
            message={documentErrorText(legalEntities.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void legalEntities.refetch()}
          />
        ) : entities === undefined ? (
          <TableLoading label={t("loadingLegalEntities")} />
        ) : entities.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t("noLegalEntities")}</p>
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
              <DocumentCreateFields legalEntities={entities} isPending={createDocument.isPending} />
              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  disabled={createDocument.isPending}
                  onClick={() => onOpenChange(false)}
                >
                  {tForm("cancel")}
                </Button>
                <Button type="submit" disabled={createDocument.isPending}>
                  {createDocument.isPending ? tForm("saving") : tForm("create")}
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
 * The fields of a new document: the relation, the shared detail fields and
 * the template. Picking a template takes over its type and language, and its
 * name while the title is still empty; a template of another legal entity is
 * dropped when the legal entity changes, as the server refuses it.
 */
function DocumentCreateFields({
  legalEntities,
  isPending,
}: {
  legalEntities: LegalEntityListItem[];
  isPending: boolean;
}) {
  const t = useTranslations("documents");
  const tRelations = useTranslations("relations");
  const tTemplates = useTranslations("documentTemplates");
  const { control, getValues, setValue } = useFormContext<DocumentCreateFormValues>();
  const relationId = useWatch({ control, name: "relationId" });
  const legalEntityId = useWatch({ control, name: "legalEntityId" });
  const templateId = useWatch({ control, name: "templateId" });
  const [picked, setPicked] = useState<DocumentTemplateListItem | null>(null);
  const relations = useRelationOptions(relationId || null);
  const templates = useTemplateOptions(legalEntityId || null, picked);

  useEffect(() => {
    if (templateId === "") {
      setPicked(null);
      return;
    }
    if (picked?.id === templateId) return;
    const template = templates.rows.find((row) => row.id === templateId);
    if (!template) return;
    setPicked(template);
    setValue("type", template.type);
    setValue("language", template.language);
    if (getValues("title").trim() === "") {
      setValue("title", template.name, { shouldValidate: true });
    }
  }, [templateId, picked, templates.rows, getValues, setValue]);

  useEffect(() => {
    if (picked?.legalEntityId && legalEntityId && picked.legalEntityId !== legalEntityId) {
      setValue("templateId", "");
    }
  }, [legalEntityId, picked, setValue]);

  return (
    <FormGrid>
      <div className="md:col-span-2">
        <SelectField
          name="relationId"
          label={t("form.relation")}
          placeholder={t("form.relationPlaceholder")}
          searchPlaceholder={t("form.relationSearchPlaceholder")}
          control={control}
          options={relations.options}
          onSearchChange={relations.onSearchChange}
          isLoading={relations.isLoading}
          required
          disabled={isPending}
        />
        {relations.error !== null && (
          <p className="text-destructive mt-1 text-sm" role="alert">
            {relationErrorText(relations.error, tRelations, "errors.loadHint")}
          </p>
        )}
      </div>
      <DocumentDetailFields legalEntities={legalEntities} isPending={isPending} />
      <div className="md:col-span-2">
        <SelectField
          name="templateId"
          label={t("form.template")}
          description={t("form.templateHint")}
          placeholder={t("form.templatePlaceholder")}
          searchPlaceholder={t("form.templateSearchPlaceholder")}
          control={control}
          options={templates.options}
          onSearchChange={templates.onSearchChange}
          isLoading={templates.isLoading}
          clearable
          disabled={isPending}
        />
        {templates.error !== null && (
          <p className="text-destructive mt-1 text-sm" role="alert">
            {documentTemplateErrorText(templates.error, tTemplates, "errors.loadHint")}
          </p>
        )}
      </div>
    </FormGrid>
  );
}
