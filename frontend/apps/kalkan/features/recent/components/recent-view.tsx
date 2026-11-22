"use client";

import { useTranslations } from "next-intl";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { PageHeading } from "@/components/page-states";
import { useRecentPages } from "@/features/pages/hooks/use-pages";
import { pageErrorText } from "@/features/pages/lib/page-error";
import type { PageRecentItem } from "@/features/pages/schemas";
import { useRecentColumns } from "../hooks/use-recent-columns";
import { DataTable } from "./data-table";

/** How many changed pages the Recent page lists. */
export const RECENT_PAGE_LIMIT = 50;

/** The pages changed most recently, across every section the user can see. */
export function RecentView() {
  const t = useTranslations("recent");
  const tPages = useTranslations("pages");
  const tErrors = useTranslations("errors");
  const recent = useRecentPages(RECENT_PAGE_LIMIT);
  const columns = useRecentColumns();
  const rows = recent.data?.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />
      {recent.isError && recent.data === undefined ? (
        <LoadError
          title={t("loadError")}
          message={pageErrorText(recent.error, tPages, "view.loadError")}
          retryLabel={tErrors("tryAgain")}
          onRetry={() => void recent.refetch()}
        />
      ) : recent.data === undefined ? (
        <TableLoading label={t("loading")} />
      ) : rows.length === 0 ? (
        <EmptyState title={t("empty")} description={t("emptyHint")} />
      ) : (
        <DataTable<PageRecentItem>
          label={t("tableLabel")}
          columns={columns}
          data={rows}
          getRowId={(page) => page.id}
        />
      )}
    </div>
  );
}
