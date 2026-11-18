"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { FilterLabel } from "@/components/page-states";
import { useDocumentTemplates } from "@/features/document-templates/hooks/use-document-templates";
import { documentTemplateErrorText } from "@/features/document-templates/lib/document-template-error";
import type { DocumentTemplateQueryRequest } from "@/features/document-templates/schemas";
import { attachableDocumentTemplates } from "../lib/reach-out";

/** The most templates one search lists; the server caps a page at 50. */
const TEMPLATE_CHOICES = 50;

export interface AttachmentPickerProps {
  /** The ids of the picked document templates. */
  value: readonly string[];
  onChange: (documentTemplateIds: string[]) => void;
  disabled?: boolean;
}

/**
 * Picks the document templates whose PDF the new mail carries as an
 * attachment, as check boxes. Only templates with their files are offered;
 * the server stores a copy of each PDF when the outreach is saved.
 */
export function AttachmentPicker({ value, onChange, disabled = false }: AttachmentPickerProps) {
  const t = useTranslations("outreaches.attachments");
  const tTemplates = useTranslations("documentTemplates");
  const tErrors = useTranslations("errors");
  const [search, setSearch] = useState<string | null>(null);
  const query = useMemo(
    (): DocumentTemplateQueryRequest => ({
      search: search ?? undefined,
      page: 1,
      amount: TEMPLATE_CHOICES,
    }),
    [search]
  );
  const templates = useDocumentTemplates(query);
  const options = useMemo(
    () => attachableDocumentTemplates(templates.data?.data ?? []),
    [templates.data]
  );

  const toggle = (templateId: string, checked: boolean) => {
    onChange(checked ? [...value, templateId] : value.filter((id) => id !== templateId));
  };

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 text-sm font-medium">{t("templatesLegend")}</legend>
      <p className="text-muted-foreground mb-1 text-sm">{t("templatesHint")}</p>
      <FilterLabel label={tTemplates("filters.searchLabel")} className="w-full">
        <SenseySearchInput
          value={search}
          placeholder={tTemplates("filters.searchPlaceholder")}
          onSearchChange={(next) => setSearch(next ?? null)}
          className="w-full"
        />
      </FilterLabel>
      {templates.isError && templates.data === undefined ? (
        <LoadError
          title={tTemplates("loadError")}
          message={documentTemplateErrorText(templates.error, tTemplates, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void templates.refetch()}
        />
      ) : templates.data === undefined ? (
        <TableLoading label={tTemplates("loading")} />
      ) : options.length === 0 ? (
        <p className="text-muted-foreground text-sm" role="status">
          {search ? tTemplates("emptyFiltered") : t("noTemplates")}
        </p>
      ) : (
        <div className="flex max-h-[30vh] flex-col gap-1 overflow-y-auto">
          {options.map((template) => (
            <label
              key={template.id}
              className="hover:bg-muted has-[:checked]:border-primary flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2"
            >
              <input
                type="checkbox"
                checked={value.includes(template.id)}
                disabled={disabled}
                onChange={(event) => toggle(template.id, event.target.checked)}
              />
              <span className="min-w-0 flex-1 truncate text-sm font-medium">{template.name}</span>
              <span className="text-muted-foreground text-sm whitespace-nowrap">
                {`${tTemplates(`types.${template.type}`)} · ${tTemplates(`languages.${template.language}`)}`}
              </span>
            </label>
          ))}
        </div>
      )}
    </fieldset>
  );
}
