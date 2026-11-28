"use client";

import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useRecentPages } from "@/features/pages/hooks/use-pages";
import { pageErrorText } from "@/features/pages/lib/page-error";
import { listedPageTitle, sectionOfRootSlug } from "@/features/recent/lib/page-label";
import { OVERVIEW_RECENT_LIMIT } from "@/features/recent/lib/recent-limits";
import { pageHref } from "@/lib/routes";

/** The latest page changes, each with its section, when and by whom. The sidebar leads to the full list. */
export function RecentChangesCard() {
  const t = useTranslations("overview");
  const tRecent = useTranslations("recent");
  const tSidebar = useTranslations("sidebar");
  const tPages = useTranslations("pages");
  const tErrors = useTranslations("errors");
  const { formatDateTime } = useDateFormat();
  const recent = useRecentPages(OVERVIEW_RECENT_LIMIT);
  const rows = recent.data?.data ?? [];

  return (
    <Card role="region" aria-labelledby="overview-recent" className="gap-4">
      <CardHeader>
        <h2 id="overview-recent" className="text-lg leading-none">
          {t("recentTitle")}
        </h2>
      </CardHeader>
      <CardContent>
        {recent.isError && recent.data === undefined ? (
          <LoadError
            title={t("recentLoadError")}
            message={pageErrorText(recent.error, tPages, "view.loadError")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void recent.refetch()}
          />
        ) : recent.data === undefined ? (
          <TableLoading label={t("recentLoading")} />
        ) : rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t("recentEmpty")}</p>
        ) : (
          <ul className="divide-y">
            {rows.map((page) => {
              const section = sectionOfRootSlug(page.rootSlug);
              return (
                <li key={page.id} className="flex flex-col gap-0.5 py-2.5 first:pt-0 last:pb-0">
                  <Link
                    href={pageHref(page.id)}
                    className="text-foreground truncate text-sm font-medium hover:underline"
                  >
                    {listedPageTitle(page)}
                  </Link>
                  <span className="text-muted-foreground flex flex-wrap gap-x-3 text-xs">
                    <span>{section ? tSidebar(`sections.${section}`) : page.rootSlug}</span>
                    <span className="tabular-nums">{formatDateTime(page.updatedAt)}</span>
                    <span>{page.updatedByName ?? tRecent("unknownUser")}</span>
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
