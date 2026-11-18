"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, FilterLabel, PageHeading } from "@/components/page-states";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useIcpColumns } from "../hooks/use-icp-columns";
import { useIcpFilterParams } from "../hooks/use-icp-filter-params";
import { useDeleteIcp, useIcps } from "../hooks/use-icps";
import { icpErrorText } from "../lib/icp-error";
import { toIcpQuery } from "../lib/icp-filters";
import type { IcpListItem } from "../schemas";
import { IcpFormDialog } from "./icp-form-dialog";

/**
 * The organization's ICPs by name, with a name search and paging in the URL.
 * Every member creates and edits them in a dialog; only an admin deletes.
 */
export function IcpsView() {
  const t = useTranslations("icps");
  const tErrors = useTranslations("errors");
  const isAdmin = useIsOrganizationAdmin();
  const [filters, setFilters] = useIcpFilterParams();
  const query = useMemo(() => toIcpQuery(filters), [filters]);
  const icps = useIcps(query);
  const deleteIcp = useDeleteIcp();
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<IcpListItem | null>(null);

  const handleNew = useCallback(() => {
    setEditingId(null);
    setFormOpen(true);
  }, []);
  const handleEdit = useCallback((icp: IcpListItem) => {
    setEditingId(icp.id);
    setFormOpen(true);
  }, []);
  const handleDelete = useCallback((icp: IcpListItem) => setDeleting(icp), []);
  const columns = useIcpColumns({ onEdit: handleEdit, onDelete: isAdmin ? handleDelete : null });

  const rows = useMemo((): IcpListItem[] => icps.data?.data ?? [], [icps.data?.data]);
  const pagination = icps.data?.pagination;
  const isFiltered = !!filters.search;

  const newButton = (
    <Button onClick={handleNew}>
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
      </div>

      {icps.isError && icps.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={icpErrorText(icps.error, t, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void icps.refetch()}
        />
      ) : icps.data === undefined ? (
        <TableLoading label={t("loading")} />
      ) : rows.length === 0 ? (
        isFiltered ? (
          <ActionEmptyState
            title={t("emptyFiltered")}
            description={t("emptyFilteredHint")}
            action={
              <Button variant="outline" onClick={() => void setFilters({ search: null, page: 1 })}>
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
          {icps.isError && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("reloadError")}
            </p>
          )}
          <DataTable<IcpListItem>
            label={t("tableLabel")}
            columns={columns}
            data={rows}
            isFetching={icps.isFetching && icps.isPlaceholderData}
            getRowId={(icp) => icp.id}
            onRowClick={handleEdit}
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

      <IcpFormDialog icpId={editingId} open={formOpen} onOpenChange={setFormOpen} />
      <ConfirmDialog
        open={deleting !== null}
        onOpenChange={(open) => !open && setDeleting(null)}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { name: deleting?.name ?? "" })}
        confirmLabel={t("deleteAction")}
        cancelLabel={t("form.cancel")}
        destructive
        onConfirm={() => {
          if (deleting) deleteIcp.mutate(deleting.id);
          setDeleting(null);
        }}
      />
    </div>
  );
}
