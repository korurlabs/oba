"use client";

import { useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle, Plus, X } from "lucide-react";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Tabs, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { DataTable, TablePagination } from "@/components/data-table";
import { ActionEmptyState, PageHeading } from "@/components/page-states";
import { useRelationColumns } from "../hooks/use-relation-columns";
import { useRelationFilterParams } from "../hooks/use-relation-filter-params";
import { useRelations } from "../hooks/use-relations";
import {
  hasActiveRelationFilter,
  RELATION_TABS,
  tabHasStage,
  toRelationQuery,
  type RelationFilterParams,
  type RelationTab,
} from "../lib/relation-filters";
import { relationErrorText } from "../lib/relation-error";
import type { RelationListItem } from "../schemas";
import { RelationFilterPanel } from "./relation-filter-panel";

function isRelationTab(value: string): value is RelationTab {
  return (RELATION_TABS as readonly string[]).includes(value);
}

/** Every filter the toolbar sets, cleared; the tab stays. */
const CLEARED_FILTERS: Partial<RelationFilterParams> = {
  search: null,
  owner: null,
  event: null,
  stage: null,
};

/**
 * The organization's relations, one tab per type, newest change first, with
 * a name search, an owner filter and, for leads and customers, a stage filter. Every part
 * of it lives in the URL.
 */
export function RelationsView() {
  const t = useTranslations("relations");
  const tErrors = useTranslations("errors");
  const router = useRouter();
  const [filters, setFilters] = useRelationFilterParams();
  const query = useMemo(() => toRelationQuery(filters), [filters]);
  const relations = useRelations(query);
  const columns = useRelationColumns({ showStage: tabHasStage(filters.tab) });
  const isFiltered = hasActiveRelationFilter(filters);
  const newRelationHref = { pathname: "/relations/new", query: { tab: filters.tab } };

  const rows = useMemo(
    (): RelationListItem[] => relations.data?.data ?? [],
    [relations.data?.data]
  );
  const pagination = relations.data?.pagination;

  const handleFilterChange = useCallback(
    (change: Partial<RelationFilterParams>) => void setFilters({ ...change, page: 1 }),
    [setFilters]
  );
  const handleTabChange = useCallback(
    (value: string) => {
      if (!isRelationTab(value)) return;
      // Each tab has its own stages; a new tab starts on its first page.
      void setFilters({ tab: value, stage: null, page: 1 });
    },
    [setFilters]
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={t("title")}
        description={t("subtitle")}
        actions={
          <Button asChild>
            <Link href={newRelationHref}>
              <Plus aria-hidden="true" />
              {t("new")}
            </Link>
          </Button>
        }
      />

      <div className="flex flex-wrap items-end gap-3">
        <Tabs value={filters.tab} onValueChange={handleTabChange}>
          <TabsList aria-label={t("tabsLabel")}>
            {RELATION_TABS.map((tab) => (
              <TabsTrigger key={tab} value={tab}>
                {t(`tabs.${tab}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        <RelationFilterPanel filters={filters} onChange={handleFilterChange} />
      </div>

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
          <ActionEmptyState
            title={t(`emptyByTab.${filters.tab}`)}
            description={t(`emptyHintByTab.${filters.tab}`)}
            action={
              <Button asChild>
                <Link href={newRelationHref}>
                  <Plus aria-hidden="true" />
                  {t("new")}
                </Link>
              </Button>
            }
          />
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
