"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus } from "lucide-react";
import { SearchInput } from "@oba/ui/sky/search-input";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { TablePagination } from "@oba/ui/sky/table-pagination";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { DataTable } from "@/components/data-table";
import { useFilterPopoverLabels, useSearchClearLabel } from "@/components/list-labels";
import { DocumentLanguageSchema, DocumentTypeSchema } from "@/features/document-templates/schemas";
import { relationErrorText } from "@/features/relations/lib/relation-error";
import { useDocumentColumns } from "../hooks/use-document-columns";
import { useDocumentFilterParams } from "../hooks/use-document-filter-params";
import { useDocuments } from "../hooks/use-documents";
import { useLegalEntities } from "../hooks/use-legal-entities";
import { useRelationOptions } from "../hooks/use-relation-options";
import { documentErrorText } from "../lib/document-error";
import { hasActiveDocumentFilter, toDocumentQuery } from "../lib/document-filters";
import type { DocumentListItem } from "../schemas";
import { DocumentCreateDialog } from "./document-create-dialog";

/** Every filter cleared; the list goes back to its first page. */
const CLEARED_FILTERS = {
  search: null,
  relation: null,
  legalEntity: null,
  language: null,
  type: null,
  page: 1,
};

/** The filters of the popover cleared; the search stays and the list goes back to its first page. */
const CLEARED_POPOVER_FILTERS = {
  relation: null,
  legalEntity: null,
  language: null,
  type: null,
  page: 1,
};

/**
 * Every document of the organization, last changed first, with a search on
 * the title, filters on relation, legal entity, language and type, and
 * paging in the URL. Every member with Otak access opens, creates and edits
 * documents; the server checks access to the owning relation.
 */
export function DocumentsView() {
  const t = useTranslations("documents");
  const searchClearLabel = useSearchClearLabel();
  const tTemplates = useTranslations("documentTemplates");
  const tRelations = useTranslations("relations");
  const tErrors = useTranslations("errors");
  const router = useRouter();
  const filterLabels = useFilterPopoverLabels();
  const [filters, setFilters] = useDocumentFilterParams();
  const query = useMemo(() => toDocumentQuery(filters), [filters]);
  const documents = useDocuments(query);
  const legalEntities = useLegalEntities();
  const relations = useRelationOptions(filters.relation);
  const [createOpen, setCreateOpen] = useState(false);

  const typeOptions = useMemo(
    () =>
      DocumentTypeSchema.options.map((value) => ({ value, label: tTemplates(`types.${value}`) })),
    [tTemplates]
  );
  const languageOptions = useMemo(
    () =>
      DocumentLanguageSchema.options.map((value) => ({
        value,
        label: tTemplates(`languages.${value}`),
      })),
    [tTemplates]
  );
  const legalEntityOptions = useMemo(() => {
    const options = (legalEntities.data?.data ?? []).map((entity) => ({
      value: entity.id,
      label: entity.shortName,
    }));
    // A legal entity from a shared URL stays selectable until the list has loaded.
    if (filters.legalEntity && !options.some((option) => option.value === filters.legalEntity)) {
      options.unshift({ value: filters.legalEntity, label: t("filters.legalEntityUnknown") });
    }
    return options;
  }, [legalEntities.data?.data, filters.legalEntity, t]);
  const columns = useDocumentColumns();

  const filterCategories: FilterCategory[] = [
    {
      key: "relation",
      label: t("filters.relationLabel"),
      mode: "single",
      value: filters.relation,
      onChange: (relation) => void setFilters({ relation, page: 1 }),
      options: relations.options,
      onSearchChange: relations.onSearchChange,
      isLoading: relations.isLoading,
      error:
        relations.error === null
          ? null
          : relationErrorText(relations.error, tRelations, "errors.loadHint"),
      searchPlaceholder: t("form.relationSearchPlaceholder"),
    },
    {
      key: "legalEntity",
      label: t("filters.legalEntityLabel"),
      mode: "single",
      value: filters.legalEntity,
      onChange: (legalEntity) => void setFilters({ legalEntity, page: 1 }),
      options: legalEntityOptions,
      isLoading: legalEntities.isPending && legalEntities.fetchStatus !== "idle",
      error: legalEntities.isError ? t("filters.legalEntitiesLoadError") : null,
    },
    {
      key: "type",
      label: t("filters.typeLabel"),
      mode: "single",
      value: filters.type,
      onChange: (value) => {
        const type = DocumentTypeSchema.safeParse(value);
        void setFilters({ type: type.success ? type.data : null, page: 1 });
      },
      options: typeOptions,
    },
    {
      key: "language",
      label: t("filters.languageLabel"),
      mode: "single",
      value: filters.language,
      onChange: (value) => {
        const language = DocumentLanguageSchema.safeParse(value);
        void setFilters({ language: language.success ? language.data : null, page: 1 });
      },
      options: languageOptions,
    },
  ];

  const rows = useMemo(
    (): DocumentListItem[] => documents.data?.data ?? [],
    [documents.data?.data]
  );
  const pagination = documents.data?.pagination;
  const isFiltered = hasActiveDocumentFilter(filters);

  const newButton = (
    <Button onClick={() => setCreateOpen(true)}>
      <Plus aria-hidden="true" />
      {t("new")}
    </Button>
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />

      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            label={t("filters.searchLabel")}
            clearLabel={searchClearLabel}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => void setFilters({ search: search ?? null, page: 1 })}
          />
        }
        filters={
          <FilterPopover
            categories={filterCategories}
            onClear={() => void setFilters(CLEARED_POPOVER_FILTERS)}
            labels={filterLabels}
          />
        }
        create={newButton}
      />

      {documents.isError && documents.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={documentErrorText(documents.error, t, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void documents.refetch()}
        />
      ) : documents.data === undefined ? (
        <TableLoading label={t("loading")} />
      ) : rows.length === 0 ? (
        <ListEmptyState
          filtered={isFiltered}
          onClearFilters={() => void setFilters(CLEARED_FILTERS)}
          create={newButton}
          labels={{
            emptyTitle: t("empty"),
            emptyDescription: t("emptyHint"),
            filteredTitle: t("emptyFiltered"),
            filteredDescription: t("emptyFilteredHint"),
            clearFilters: t("filters.clear"),
          }}
        />
      ) : (
        <>
          {documents.isError && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("reloadError")}
            </p>
          )}
          <DataTable<DocumentListItem>
            label={t("tableLabel")}
            columns={columns}
            data={rows}
            isFetching={documents.isFetching && documents.isPlaceholderData}
            getRowId={(document) => document.id}
            onRowClick={(document) => router.push(`/documents/${document.id}`)}
          />
          {pagination && pagination.totalPages > 1 && (
            <TablePagination
              page={pagination.currentPage}
              totalPages={pagination.totalPages}
              onPageChange={(page) => void setFilters({ page })}
              label={t("pagination.label")}
              previousLabel={t("pagination.previous")}
              nextLabel={t("pagination.next")}
              summary={t("pagination.summary", {
                page: pagination.currentPage,
                pages: pagination.totalPages,
                total: pagination.totalElements,
              })}
            />
          )}
        </>
      )}

      <DocumentCreateDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        relationId={filters.relation}
      />
    </div>
  );
}
