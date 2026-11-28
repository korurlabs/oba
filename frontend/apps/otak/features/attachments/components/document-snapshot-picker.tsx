"use client";

import { useId, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { FilePlus, Plus } from "lucide-react";
import { SelectFieldStandalone, type SelectOption } from "@oba/shared/features/form";
import { Button } from "@oba/ui/sky/button";
import { NativeSelect } from "@oba/ui/sky/field";
import { FilterLabel } from "@oba/ui/sky/filter-label";
import { DocumentTypeSchema, type DocumentType } from "@/features/document-templates/schemas";
import { documentErrorText } from "@/features/documents/lib/document-error";
import { useAttachDocumentSnapshot } from "../hooks/use-document-snapshots";
import { useSnapshotDocumentOptions } from "../hooks/use-snapshot-document-options";
import { NEW_FROM_TEMPLATE, withNewFromTemplate } from "../lib/document-snapshots";
import { DocumentFromTemplateDialog } from "./document-from-template-dialog";

export interface DocumentSnapshotPickerProps {
  mailId: string;
  /** The mail's relation; only its documents can go with the mail. */
  relationId: string;
}

/**
 * Picks one of the relation's documents with a PDF and attaches a copy of
 * that PDF to the mail. The copy stays as it is when the document changes
 * later, so the mail sends what the user saw when adding it. The last entry
 * of the select makes a new document from a template and attaches it at once.
 */
export function DocumentSnapshotPicker({ mailId, relationId }: DocumentSnapshotPickerProps) {
  const t = useTranslations("outreaches.mailAttachments");
  const tDocuments = useTranslations("documents");
  const tTemplates = useTranslations("documentTemplates");
  const typeId = useId();
  const [type, setType] = useState<DocumentType | null>(null);
  const [documentId, setDocumentId] = useState("");
  // The picked document stays an option when a later search no longer finds it, so the select keeps its label.
  const [picked, setPicked] = useState<SelectOption | null>(null);
  const [fromTemplateOpen, setFromTemplateOpen] = useState(false);
  const documents = useSnapshotDocumentOptions(relationId, type);
  const options = useMemo(
    () =>
      withNewFromTemplate(
        picked && !documents.options.some((option) => option.value === picked.value)
          ? [picked, ...documents.options]
          : documents.options,
        t("newFromTemplate")
      ),
    [documents.options, picked, t]
  );

  const pick = (id: string) => {
    if (id === NEW_FROM_TEMPLATE) {
      setFromTemplateOpen(true);
      return;
    }
    setDocumentId(id);
    setPicked(options.find((option) => option.value === id) ?? null);
  };
  const attach = useAttachDocumentSnapshot();

  const typeOptions = useMemo(
    () =>
      DocumentTypeSchema.options.map((value) => ({ value, label: tTemplates(`types.${value}`) })),
    [tTemplates]
  );

  const handleAdd = () => {
    if (documentId === "") return;
    attach.mutate({ documentId, mailId }, { onSuccess: () => pick("") });
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <FilterLabel label={t("typeLabel")} htmlFor={typeId} className="w-full sm:w-40">
          <NativeSelect
            id={typeId}
            value={type ?? ""}
            onChange={(event) => {
              const picked = DocumentTypeSchema.safeParse(event.target.value);
              setType(picked.success ? picked.data : null);
              pick("");
            }}
            className="w-full"
          >
            <option value="">{t("typeAll")}</option>
            {typeOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </NativeSelect>
        </FilterLabel>
        <FilterLabel label={t("documentLabel")} className="w-full min-w-0 flex-1">
          <SelectFieldStandalone
            value={documentId}
            onValueChange={pick}
            options={options}
            onSearchChange={documents.onSearchChange}
            isLoading={documents.isLoading}
            renderOption={(option) =>
              option.value === NEW_FROM_TEMPLATE ? (
                <span className="text-primary flex items-center gap-2">
                  <Plus className="h-4 w-4 shrink-0" aria-hidden="true" />
                  {option.label}
                </span>
              ) : (
                <span className="truncate">{option.label}</span>
              )
            }
            placeholder={t("documentPlaceholder")}
            searchPlaceholder={t("documentSearchPlaceholder")}
            disabled={attach.isPending}
            className="w-full"
          />
        </FilterLabel>
        <Button
          type="button"
          disabled={documentId === "" || attach.isPending}
          onClick={handleAdd}
          className="shrink-0"
        >
          <FilePlus aria-hidden="true" />
          {attach.isPending ? t("adding") : t("add")}
        </Button>
      </div>
      {documents.error !== null && (
        <p className="text-destructive text-sm" role="alert">
          {documentErrorText(documents.error, tDocuments, "errors.loadHint")}
        </p>
      )}
      {documents.error === null &&
        !documents.isLoading &&
        documents.options.length === 0 &&
        type === null &&
        documents.search === "" && (
          <p className="text-muted-foreground text-sm" role="status">
            {t("noDocuments")}
          </p>
        )}
      <DocumentFromTemplateDialog
        open={fromTemplateOpen}
        onOpenChange={setFromTemplateOpen}
        mailId={mailId}
        relationId={relationId}
      />
    </div>
  );
}
