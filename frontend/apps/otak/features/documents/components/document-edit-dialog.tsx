"use client";

import { useMemo } from "react";
import type { UseFormReturn } from "react-hook-form";
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
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useUpdateDocument } from "../hooks/use-documents";
import { useLegalEntities } from "../hooks/use-legal-entities";
import { documentErrorText } from "../lib/document-error";
import {
  createDocumentEditFormSchema,
  documentToEditFormValues,
  toDocumentUpdateRequest,
  type DocumentDetail,
  type DocumentEditFormValues,
} from "../schemas";
import { applyDocumentFieldErrors } from "./apply-document-field-errors";
import { DocumentDetailFields } from "./document-form-fields";

export interface DocumentEditDialogProps {
  document: DocumentDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Edits a document's title, type, language and legal entity; closes once the
 * server has stored them. The owner and the files stay as they are.
 */
export function DocumentEditDialog({ document, open, onOpenChange }: DocumentEditDialogProps) {
  const t = useTranslations("documents");
  const tErrors = useTranslations("errors");
  const tForm = useTranslations("documents.form");
  const schema = useMemo(() => createDocumentEditFormSchema(tForm), [tForm]);
  const legalEntities = useLegalEntities();
  const updateDocument = useUpdateDocument();
  const entities = legalEntities.data?.data;

  const handleSubmit = (
    values: DocumentEditFormValues,
    form: UseFormReturn<DocumentEditFormValues>
  ) => {
    updateDocument.mutate(
      { documentId: document.id, data: toDocumentUpdateRequest(values) },
      {
        onSuccess: () => onOpenChange(false),
        onError: (error) => applyDocumentFieldErrors(form, error, t),
      }
    );
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !updateDocument.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("editTitle")}</DialogTitle>
          <DialogDescription>{t("editDescription")}</DialogDescription>
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
        ) : (
          // Mounted per open, so a reopened dialog starts from the stored document.
          open && (
            <ObaForm
              key={`${document.id}-${document.updatedAt}`}
              schema={schema}
              defaultValues={documentToEditFormValues(document)}
              onSubmit={handleSubmit}
              className="space-y-6"
            >
              <FormGrid>
                <DocumentDetailFields
                  legalEntities={entities}
                  isPending={updateDocument.isPending}
                />
              </FormGrid>
              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  disabled={updateDocument.isPending}
                  onClick={() => onOpenChange(false)}
                >
                  {tForm("cancel")}
                </Button>
                <Button type="submit" disabled={updateDocument.isPending}>
                  {updateDocument.isPending ? tForm("saving") : tForm("save")}
                </Button>
              </DialogFooter>
            </ObaForm>
          )
        )}
      </DialogContent>
    </Dialog>
  );
}
