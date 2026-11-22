"use client";

import { useId, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { SelectFieldStandalone } from "@oba/shared/features/form";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { NativeSelect } from "@oba/ui/sky/field";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, FilterLabel, PageHeading } from "@/components/page-states";
import { DocumentLanguageSchema, DocumentTypeSchema } from "@/features/document-templates/schemas";
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

/**
 * Every document of the organization, last changed first, with a search on
 * the title, filters on relation, legal entity, language and type, and
 * paging in the URL. Every member with Otak access opens, creates and edits
 * documents; the server checks access to the owning relation.
 */
export function DocumentsView() {
  const t = useTranslations("documents");
  const tTemplates = useTranslations("documentTemplates");
  const tErrors = useTranslations("errors");
  const router = useRouter();
  const legalEntityId = useId();
  const typeId = useId();
  const languageId = useId();
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
  const columns = useDocumentColumns();

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
      <PageHeading title={t("title")} description={t("subtitle")} actions={newButton} />

      <div className="flex flex-wrap items-end gap-3">
        <FilterLabel label={t("filters.searchLabel")} className="w-full sm:w-64">
          <SenseySearchInput
            value={filters.search}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => void setFilters({ search: search ?? null, page: 1 })}
            className="w-full"
          />
        </FilterLabel>
        <FilterLabel label={t("filters.relationLabel")} className="w-full sm:w-64">
          <SelectFieldStandalone
            value={filters.relation ?? ""}
            onValueChange={(relation) => void setFilters({ relation: relation || null, page: 1 })}
            options={relations.options}
            onSearchChange={relations.onSearchChange}
            isLoading={relations.isLoading}
            placeholder={t("filters.relationAll")}
            searchPlaceholder={t("form.relationSearchPlaceholder")}
            clearable
            className="w-full"
          />
        </FilterLabel>
        <FilterLabel
          label={t("filters.legalEntityLabel")}
          htmlFor={legalEntityId}
          className="w-full sm:w-48"
        >
          <NativeSelect
            id={legalEntityId}
            value={filters.legalEntity ?? ""}
            onChange={(event) =>
              void setFilters({ legalEntity: event.target.value || null, page: 1 })
            }
            className="w-full"
          >
            <option value="">{t("filters.legalEntityAll")}</option>
            {/* A legal entity from a shared URL stays selectable until the list has loaded. */}
            {filters.legalEntity &&
              !legalEntities.data?.data?.some((entity) => entity.id === filters.legalEntity) && (
                <option value={filters.legalEntity}>{t("filters.legalEntityUnknown")}</option>
              )}
            {(legalEntities.data?.data ?? []).map((entity) => (
              <option key={entity.id} value={entity.id}>
                {entity.shortName}
              </option>
            ))}
          </NativeSelect>
        </FilterLabel>
        <FilterLabel label={t("filters.typeLabel")} htmlFor={typeId} className="w-full sm:w-44">
          <NativeSelect
            id={typeId}
            value={filters.type ?? ""}
            onChange={(event) => {
              const type = DocumentTypeSchema.safeParse(event.target.value);
              void setFilters({ type: type.success ? type.data : null, page: 1 });
            }}
            className="w-full"
          >
            <option value="">{t("filters.typeAll")}</option>
            {typeOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </NativeSelect>
        </FilterLabel>
        <FilterLabel
          label={t("filters.languageLabel")}
          htmlFor={languageId}
          className="w-full sm:w-40"
        >
          <NativeSelect
            id={languageId}
            value={filters.language ?? ""}
            onChange={(event) => {
              const language = DocumentLanguageSchema.safeParse(event.target.value);
              void setFilters({ language: language.success ? language.data : null, page: 1 });
            }}
            className="w-full"
          >
            <option value="">{t("filters.languageAll")}</option>
            {languageOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </NativeSelect>
        </FilterLabel>
        {isFiltered && (
          <Button variant="ghost" className="h-9" onClick={() => void setFilters(CLEARED_FILTERS)}>
            <X aria-hidden="true" />
            {t("filters.clear")}
          </Button>
        )}
      </div>

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
        isFiltered ? (
          <ActionEmptyState
            title={t("emptyFiltered")}
            description={t("emptyFilteredHint")}
            action={
              <Button variant="outline" onClick={() => void setFilters(CLEARED_FILTERS)}>
                <X aria-hidden="true" />
                {t("filters.clear")}
              </Button>
            }
          />
        ) : (
          <ActionEmptyState title={t("empty")} description={t("emptyHint")} action={newButton} />
        )
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
