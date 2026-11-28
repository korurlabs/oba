"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { Button } from "@oba/ui/sky/button";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, PageHeading, useFilterPopoverLabels } from "@/components/page-states";
import { DocumentLanguageSchema } from "@/features/document-templates/schemas";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useMailTemplateColumns } from "../hooks/use-mail-template-columns";
import { useMailTemplateFilterParams } from "../hooks/use-mail-template-filter-params";
import { useDeleteMailTemplate, useMailTemplates } from "../hooks/use-mail-templates";
import { mailTemplateErrorText } from "../lib/mail-template-error";
import { hasActiveMailTemplateFilter, toMailTemplateQuery } from "../lib/mail-template-filters";
import type { MailTemplateListItem } from "../schemas";
import { MailTemplateFormDialog } from "./mail-template-form-dialog";
import { MailTemplatePreviewDialog } from "./mail-template-preview-dialog";

/** Every filter cleared; the list goes back to its first page. */
const CLEARED_FILTERS = { search: null, language: null, page: 1 };

/**
 * The organization's mail templates by name, with a search on name, a
 * language filter and paging in the URL. Every member opens a mail
 * template's preview; an admin also creates, edits and deletes them, as the
 * server refuses a member's writes.
 */
export function MailTemplatesView() {
  const t = useTranslations("mailTemplates");
  const tTemplates = useTranslations("documentTemplates");
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const filterLabels = useFilterPopoverLabels();
  const [filters, setFilters] = useMailTemplateFilterParams();
  const query = useMemo(() => toMailTemplateQuery(filters), [filters]);
  const templates = useMailTemplates(query);
  const deleteTemplate = useDeleteMailTemplate();
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<MailTemplateListItem | null>(null);

  const handleNew = useCallback(() => {
    setEditingId(null);
    setFormOpen(true);
  }, []);
  const handleEdit = useCallback((template: MailTemplateListItem) => {
    setEditingId(template.id);
    setFormOpen(true);
  }, []);
  const handlePreview = useCallback(
    (template: MailTemplateListItem) => setPreviewId(template.id),
    []
  );
  const handleDelete = useCallback((template: MailTemplateListItem) => setDeleting(template), []);
  const columns = useMailTemplateColumns({
    onPreview: handlePreview,
    onEdit: isAdmin ? handleEdit : null,
    onDelete: isAdmin ? handleDelete : null,
  });

  const languageOptions = useMemo(
    () =>
      DocumentLanguageSchema.options.map((value) => ({
        value,
        label: tTemplates(`languages.${value}`),
      })),
    [tTemplates]
  );
  const filterCategories: FilterCategory[] = [
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
    (): MailTemplateListItem[] => templates.data?.data ?? [],
    [templates.data?.data]
  );
  const pagination = templates.data?.pagination;
  const isFiltered = hasActiveMailTemplateFilter(filters);

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
          <SenseySearchInput
            value={filters.search}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => void setFilters({ search: search ?? null, page: 1 })}
            className="w-40 sm:w-56"
          />
        }
        filters={
          <FilterPopover
            categories={filterCategories}
            onClear={() => void setFilters({ language: null, page: 1 })}
            labels={filterLabels}
          />
        }
        create={newButton}
      />

      {templates.isError && templates.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={mailTemplateErrorText(templates.error, t, "errors.loadHint")}
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
          <DataTable<MailTemplateListItem>
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

      <MailTemplatePreviewDialog
        mailTemplateId={previewId}
        onOpenChange={(open) => !open && setPreviewId(null)}
      />
      {isAdmin && (
        <>
          <MailTemplateFormDialog
            mailTemplateId={editingId}
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
