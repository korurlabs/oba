"use client";

import { useCallback, useId, useMemo, useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { Button } from "@oba/ui/sky/button";
import { NativeSelect } from "@oba/ui/sky/field";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, FilterLabel, PageHeading } from "@/components/page-states";
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
const CLEARED_FILTERS = { search: null, type: null, language: null, page: 1 };

export interface DocumentTemplatesViewProps {
  /** The page's tabs, shown before the filters. */
  tabs?: ReactNode;
}

/**
 * The organization's document templates by name, with a search on name,
 * type and language filters and paging in the URL. Every member opens a
 * template's preview; an admin also creates, edits and deletes them, as the
 * server refuses a member's writes.
 */
export function DocumentTemplatesView({ tabs }: DocumentTemplatesViewProps) {
  const t = useTranslations("documentTemplates");
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const typeId = useId();
  const languageId = useId();
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
      <PageHeading title={t("title")} description={t("subtitle")} actions={newButton} />

      <div className="flex flex-wrap items-end gap-3">
        {tabs}
        <FilterLabel label={t("filters.searchLabel")} className="w-full sm:w-64">
          <SenseySearchInput
            value={filters.search}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => void setFilters({ search: search ?? null, page: 1 })}
            className="w-full"
          />
        </FilterLabel>
        <FilterLabel label={t("filters.typeLabel")} htmlFor={typeId} className="w-full sm:w-48">
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
          <ActionEmptyState
            title={t("empty")}
            description={isAdmin ? t("emptyHint") : t("emptyHintMember")}
            action={newButton}
          />
        )
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
