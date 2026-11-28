"use client";

import { useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, PageHeading } from "@/components/page-states";
import { useRelationColumns } from "../hooks/use-relation-columns";
import { useRelationFilterParams } from "../hooks/use-relation-filter-params";
import { useRelations } from "../hooks/use-relations";
import {
  hasActiveRelationFilter,
  toRelationQuery,
  type RelationFilterParams,
} from "../lib/relation-filters";
import { relationErrorText } from "../lib/relation-error";
import type { RelationListItem } from "../schemas";
import { RelationFilterPanel } from "./relation-filter-panel";

/** Every filter the toolbar sets, cleared. */
const CLEARED_FILTERS: Partial<RelationFilterParams> = {
  type: null,
  search: null,
  owner: null,
  event: null,
  campaign: null,
  stage: null,
};

/**
 * The organization's relations of every type in one list, newest change
 * first, with a name search and filters on type, stage, owner, event and
 * campaign. Every part of it lives in the URL.
 */
export function RelationsView() {
  const t = useTranslations("relations");
  const tErrors = useTranslations("errors");
  const router = useRouter();
  const [filters, setFilters] = useRelationFilterParams();
  const query = useMemo(() => toRelationQuery(filters), [filters]);
  const relations = useRelations(query);
  const columns = useRelationColumns();
  const isFiltered = hasActiveRelationFilter(filters);

  const rows = useMemo(
    (): RelationListItem[] => relations.data?.data ?? [],
    [relations.data?.data]
  );
  const pagination = relations.data?.pagination;
  const newButton = (
    <Button asChild>
      <Link href="/relations/new">
        <Plus aria-hidden="true" />
        {t("new")}
      </Link>
    </Button>
  );

  const handleFilterChange = useCallback(
    (change: Partial<RelationFilterParams>) => void setFilters({ ...change, page: 1 }),
    [setFilters]
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />

      <ListToolbar
        search={
          <SenseySearchInput
            value={filters.search}
            placeholder={t("filters.searchPlaceholder")}
            onSearchChange={(search) => handleFilterChange({ search: search ?? null })}
            className="w-40 sm:w-56"
          />
        }
        filters={<RelationFilterPanel filters={filters} onChange={handleFilterChange} />}
        create={newButton}
      />

      {relations.isError && relations.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={relationErrorText(relations.error, t, "errors.loadHint")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void relations.refetch()}
        />
      ) : relations.data === undefined ? (
        <TableLoading label={t("loading")} />
      ) : rows.length === 0 ? (
        isFiltered ? (
          <ActionEmptyState
            title={t("emptyFiltered")}
            description={t("emptyFilteredHint")}
            action={
              <Button variant="outline" onClick={() => handleFilterChange(CLEARED_FILTERS)}>
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
          {relations.isError && (
            <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {t("reloadError")}
            </p>
          )}
          <DataTable<RelationListItem>
            label={t("tableLabel")}
            columns={columns}
            data={rows}
            isFetching={relations.isFetching && relations.isPlaceholderData}
            getRowId={(relation) => relation.id}
            onRowClick={(relation) => router.push(`/relations/${relation.id}`)}
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
    </div>
  );
}
