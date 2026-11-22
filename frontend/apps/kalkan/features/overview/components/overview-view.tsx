"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { PageHeading } from "@/components/page-states";
import { usePageTree } from "@/features/pages/hooks/use-pages";
import { pageErrorText } from "@/features/pages/lib/page-error";
import { sectionCounts } from "../lib/section-counts";
import { RecentChangesCard } from "./recent-changes-card";
import { SectionCards } from "./section-cards";

/**
 * The start page: every section with its page count, counted from the page
 * tree the sidebar already reads, and the latest changes.
 */
export function OverviewView() {
  const t = useTranslations("overview");
  const tPages = useTranslations("pages");
  const tErrors = useTranslations("errors");
  const tree = usePageTree();
  const pages = tree.data?.data;
  const counts = useMemo(() => (pages ? sectionCounts(pages) : undefined), [pages]);

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div aria-label={t("sectionsLabel")} role="region">
          {tree.isError && counts === undefined ? (
            <LoadError
              title={t("loadError")}
              message={pageErrorText(tree.error, tPages, "view.loadError")}
              retryLabel={tErrors("tryAgain")}
              onRetry={() => void tree.refetch()}
            />
          ) : counts === undefined ? (
            <TableLoading label={t("loading")} />
          ) : (
            <SectionCards counts={counts} />
          )}
        </div>
        <RecentChangesCard />
      </div>
    </div>
  );
}
