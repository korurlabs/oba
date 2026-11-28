"use client";

import { useCallback, useState, type ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { parseAsString, useQueryState } from "nuqs";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { useRestorePage, useTrash } from "@/features/pages/hooks/use-pages";
import { pageErrorText } from "@/features/pages/lib/page-error";
import type { PageTrashItem } from "@/features/pages/schemas";
import { DataTable } from "@/components/data-table";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { useTrashColumns } from "../hooks/use-trash-columns";
import { needsParentRestoredFirst } from "../lib/restore-refusal";
import { trashItemMatchesSearch } from "../lib/trash-search";

/**
 * The deleted pages the user can restore. The server refuses a guest, so a
 * guest is told the trash is for members instead of seeing a refusal. A
 * restore whose parent is in the trash too is marked on its row, besides the
 * toast, so the user sees which page to restore first. The trash arrives
 * whole, so the search narrows it in the browser.
 */
export function TrashView() {
  const t = useTranslations("trash");
  const tPages = useTranslations("pages");
  const tErrors = useTranslations("errors");
  const locale = useLocale();
  const [search, setSearch] = useQueryState("search", parseAsString);
  const [searchReset, setSearchReset] = useState(0);
  const me = useGetMe();
  const isMember = useIsOrganizationMember();
  const trash = useTrash({ enabled: isMember });
  const restore = useRestorePage();
  const restorePage = restore.mutate;
  const [parentFirstIds, setParentFirstIds] = useState<ReadonlySet<string>>(new Set());

  const handleRestore = useCallback(
    (page: PageTrashItem) => {
      restorePage(page.id, {
        // The hook's toast reports every refusal; this only marks the row.
        onError: (error: unknown) => {
          if (needsParentRestoredFirst(error)) {
            setParentFirstIds((current) => new Set(current).add(page.id));
          }
        },
      });
    },
    [restorePage]
  );
  const columns = useTrashColumns({
    onRestore: handleRestore,
    restoringId: restore.isPending ? (restore.variables ?? null) : null,
    parentFirstIds,
  });
  const all = trash.data?.data ?? [];
  const rows = search ? all.filter((item) => trashItemMatchesSearch(item, search, locale)) : all;

  let body: ReactNode;
  if (!isMember) {
    body =
      me.data === undefined && !me.isError ? (
        <TableLoading label={t("loading")} />
      ) : (
        <EmptyState title={t("membersOnly")} description={t("membersOnlyHint")} />
      );
  } else if (trash.isError && trash.data === undefined) {
    body = (
      <LoadError
        title={t("loadError")}
        message={pageErrorText(trash.error, tPages, "view.loadError")}
        retryLabel={tErrors("tryAgain")}
        onRetry={() => void trash.refetch()}
      />
    );
  } else if (trash.data === undefined) {
    body = <TableLoading label={t("loading")} />;
  } else if (rows.length === 0) {
    body = (
      <ListEmptyState
        filtered={all.length > 0}
        onClearFilters={() => {
          void setSearch(null);
          setSearchReset((count) => count + 1);
        }}
        labels={{
          emptyTitle: t("empty"),
          emptyDescription: t("emptyHint"),
          filteredTitle: t("filtered.title"),
          filteredDescription: t("filtered.description"),
          clearFilters: t("filtered.clear"),
        }}
      />
    );
  } else {
    body = (
      <DataTable<PageTrashItem>
        label={t("tableLabel")}
        columns={columns}
        data={rows}
        getRowId={(page) => page.id}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />
      {isMember && (
        <ListToolbar
          search={
            <SearchInput
              value={search}
              onSearchChange={(next) => void setSearch(next ?? null)}
              label={t("search")}
              clearLabel={t("searchClear")}
              resetSignal={searchReset}
            />
          }
        />
      )}
      {body}
    </div>
  );
}
