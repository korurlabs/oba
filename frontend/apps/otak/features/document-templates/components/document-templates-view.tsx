"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus } from "lucide-react";
import { SearchInput } from "@oba/ui/sky/search-input";
import { Button } from "@oba/ui/sky/button";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { TablePagination } from "@oba/ui/sky/table-pagination";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable } from "@/components/data-table";
import { useFilterPopoverLabels, useSearchClearLabel } from "@/components/list-labels";
import { useLegalEntities } from "@/features/documents/hooks/use-legal-entities";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useDocumentTemplateColumns } from "../hooks/use-document-template-columns";
import { useDocumentTemplateFilterParams } from "../hooks/use-document-template-filter-params";
import { useDeleteDocumentTemplate, useDocumentTemplates } from "../hooks/use-document-templates";
import { documentTemplateErrorText } from "../lib/document-template-error";
import {
  hasActiveDocumentTemplateFilter,
  toDocumentTemplateQuery,
} from "../lib/document-template-filters";
import {
  DocumentLanguageSchema,
  DocumentTypeSchema,
  type DocumentTemplateListItem,
} from "../schemas";
import { DocumentTemplateFormDialog } from "./document-template-form-dialog";
import { DocumentTemplatePreviewDialog } from "./document-template-preview-dialog";

/** Every filter cleared; the list goes back to its first page. */
const CLEARED_FILTERS = { search: null, type: null, language: null, legalEntity: null, page: 1 };

/** The filters of the popover cleared; the search stays and the list goes back to its first page. */
const CLEARED_POPOVER_FILTERS = { type: null, language: null, legalEntity: null, page: 1 };

/**
 * The organization's document templates by name, with a search on name,
 * type, language and legal entity filters and paging in the URL. The legal
 * entity filter keeps that legal entity's own templates and those every
 * legal entity may use. Every member opens a
 * template's preview; an admin also creates, edits and deletes them, as the
 * server refuses a member's writes.
 */
export function DocumentTemplatesView() {
  const t = useTranslations("documentTemplates");
  const searchClearLabel = useSearchClearLabel();
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const filterLabels = useFilterPopoverLabels();
  const legalEntities = useLegalEntities();
  const [filters, setFilters] = useDocumentTemplateFilterParams();
  const query = useMemo(() => toDocumentTemplateQuery(filters), [filters]);
  const templates = useDocumentTemplates(query);
  const deleteTemplate = useDeleteDocumentTemplate();
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<DocumentTemplateListItem | null>(null);

  const handleNew = useCallback(() => {
    setEditingId(null);
    setFormOpen(true);
  }, []);
  const handleEdit = useCallback((template: DocumentTemplateListItem) => {
    setEditingId(template.id);
    setFormOpen(true);
  }, []);
  const handlePreview = useCallback(
    (template: DocumentTemplateListItem) => setPreviewId(template.id),
    []
  );
  const handleDelete = useCallback(
    (template: DocumentTemplateListItem) => setDeleting(template),
    []
  );
  const columns = useDocumentTemplateColumns({
    onPreview: handlePreview,
    onEdit: isAdmin ? handleEdit : null,
    onDelete: isAdmin ? handleDelete : null,
  });

  const typeOptions = useMemo(
    () => DocumentTypeSchema.options.map((value) => ({ value, label: t(`types.${value}`) })),
    [t]
  );
  const languageOptions = useMemo(
    () =>
      DocumentLanguageSchema.options.map((value) => ({ value, label: t(`languages.${value}`) })),
    [t]
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

  const filterCategories: FilterCategory[] = [
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
  ];

  const rows = useMemo(
    (): DocumentTemplateListItem[] => templates.data?.data ?? [],
    [templates.data?.data]
  );
  const pagination = templates.data?.pagination;
  const isFiltered = hasActiveDocumentTemplateFilter(filters);

  const newButton = isAdmin ? (
    <Button onClick={handleNew}>
      <Plus aria-hidden="true" />
      {t("new")}
    </Button>
  ) : undefined;

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

      {templates.isError && templates.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={documentTemplateErrorText(templates.error, t, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void templates.refetch()}
        />
      ) : templates.data === undefined ? (
        <TableLoading label={t("loading")} />
      ) : rows.length === 0 ? (
        <ListEmptyState
          filtered={isFiltered}
          onClearFilters={() => void setFilters(CLEARED_FILTERS)}
          create={newButton}
          labels={{
            emptyTitle: t("empty"),
            emptyDescription: isAdmin ? t("emptyHint") : t("emptyHintMember"),
            filteredTitle: t("emptyFiltered"),
            filteredDescription: t("emptyFilteredHint"),
            clearFilters: t("filters.clear"),
          }}
        />
      ) : (
        <>
          {templates.isError && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("reloadError")}
            </p>
          )}
          <DataTable<DocumentTemplateListItem>
            label={t("tableLabel")}
            columns={columns}
            data={rows}
            isFetching={templates.isFetching && templates.isPlaceholderData}
            getRowId={(template) => template.id}
            onRowClick={isAdmin ? handleEdit : handlePreview}
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

      <DocumentTemplatePreviewDialog
        templateId={previewId}
        onOpenChange={(open) => !open && setPreviewId(null)}
      />
      {isAdmin && (
        <>
          <DocumentTemplateFormDialog
            templateId={editingId}
            open={formOpen}
            onOpenChange={setFormOpen}
          />
          <ConfirmDialog
            open={deleting !== null}
            onOpenChange={(open) => !open && setDeleting(null)}
            title={t("deleteTitle")}
            description={t("deleteConfirm", { name: deleting?.name ?? "" })}
            confirmLabel={t("deleteAction")}
            cancelLabel={t("form.cancel")}
            destructive
            onConfirm={() => {
              if (deleting) deleteTemplate.mutate(deleting.id);
              setDeleting(null);
            }}
          />
        </>
      )}
    </div>
  );
}
