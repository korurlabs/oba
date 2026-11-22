"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import type { SelectOption } from "@oba/shared/features/form";
import type { DocumentType } from "@/features/document-templates/schemas";
import { useDocuments } from "@/features/documents/hooks/use-documents";
import { attachableDocuments, snapshotPickerQuery } from "../lib/document-snapshots";

/**
 * The options of a mail's document select: the relation's documents with a
 * PDF, searched by title on the server and narrowed by type.
 */
export function useSnapshotDocumentOptions(relationId: string, type: DocumentType | null) {
  const tTemplates = useTranslations("documentTemplates");
  const [search, setSearch] = useState("");
  const query = useMemo(
    () => snapshotPickerQuery(relationId, search, type),
    [relationId, search, type]
  );
  const documents = useDocuments(query);

  const options = useMemo(
    (): SelectOption[] =>
      attachableDocuments(documents.data?.data ?? []).map((document) => ({
        value: document.id,
        label: [
          document.title,
          tTemplates(`types.${document.type}`),
          tTemplates(`languages.${document.language}`),
        ].join(" · "),
      })),
    [documents.data?.data, tTemplates]
  );

  return {
    options,
    isLoading: documents.data === undefined && documents.isFetching,
    search,
    onSearchChange: setSearch,
    error: documents.isError ? documents.error : null,
  };
}
