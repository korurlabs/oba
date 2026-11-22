"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import type { SelectOption } from "@oba/shared/features/form";
import { useDocumentTemplates } from "@/features/document-templates/hooks/use-document-templates";
import type { DocumentTemplateListItem } from "@/features/document-templates/schemas";
import { templatePickerQuery } from "../lib/picker-queries";

/**
 * The options of the template select of a new document, searched by name on
 * the server. A template without its files can be picked, marked as such;
 * the document then starts without files. `picked` stays among the options
 * when a later search no longer finds it, so the select keeps its label.
 */
export function useTemplateOptions(
  legalEntityId: string | null,
  picked: DocumentTemplateListItem | null
) {
  const tTemplates = useTranslations("documentTemplates");
  const [search, setSearch] = useState("");
  const query = useMemo(() => templatePickerQuery(search, legalEntityId), [search, legalEntityId]);
  const templates = useDocumentTemplates(query);

  const rows = useMemo((): DocumentTemplateListItem[] => {
    const found = templates.data?.data ?? [];
    return picked && !found.some((template) => template.id === picked.id)
      ? [picked, ...found]
      : found;
  }, [templates.data?.data, picked]);

  const options = useMemo(
    (): SelectOption[] =>
      rows.map((template) => ({
        value: template.id,
        label: [
          template.name,
          tTemplates(`types.${template.type}`),
          tTemplates(`languages.${template.language}`),
          ...(template.ready ? [] : [tTemplates("ready.no")]),
        ].join(" · "),
      })),
    [rows, tTemplates]
  );

  return {
    rows,
    options,
    isLoading: templates.data === undefined && templates.isFetching,
    onSearchChange: setSearch,
    error: templates.isError ? templates.error : null,
  };
}
