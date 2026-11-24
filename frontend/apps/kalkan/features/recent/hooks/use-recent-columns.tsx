"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link } from "@oba/shared/libs/i18n/navigation";
import type { PageRecentItem } from "@/features/pages/schemas";
import { pageHref } from "@/lib/routes";
import { listedPageTitle, sectionOfRootSlug } from "../lib/page-label";

/**
 * The recent list: the page takes the free width and links to it, then its
 * section, when it changed and who changed it.
 */
export function useRecentColumns() {
  const t = useTranslations("recent");
  const tSidebar = useTranslations("sidebar");
  const { formatDate, formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<PageRecentItem, unknown>[]>(
    () => [
      {
        id: "title",
        header: t("columns.title"),
        cell: ({ row }) => (
          <div className="flex min-w-0 flex-col">
            <Link
              href={pageHref(row.original.id)}
              className="text-foreground block truncate text-sm font-medium hover:underline"
            >
              {listedPageTitle(row.original)}
            </Link>
            <span className="text-muted-foreground block truncate text-xs">
              {row.original.path}
            </span>
          </div>
        ),
        meta: { flex: true },
      },
      {
        id: "section",
        header: t("columns.section"),
        size: 160,
        cell: ({ row }) => {
          const section = sectionOfRootSlug(row.original.rootSlug);
          return (
            <span className="text-muted-foreground block truncate text-sm">
              {section ? tSidebar(`sections.${section}`) : row.original.rootSlug}
            </span>
          );
        },
      },
      {
        id: "updatedAt",
        header: t("columns.updatedAt"),
        size: 130,
        cell: ({ row }) => (
          <span
            className="text-muted-foreground text-sm whitespace-nowrap tabular-nums"
            title={formatDateTime(row.original.updatedAt)}
          >
            {formatDate(row.original.updatedAt)}
          </span>
        ),
      },
      {
        id: "updatedBy",
        header: t("columns.updatedBy"),
        size: 180,
        cell: ({ row }) => (
          <span className="text-muted-foreground block truncate text-sm">
            {row.original.updatedByName ?? t("unknownUser")}
          </span>
        ),
      },
    ],
    [t, tSidebar, formatDate, formatDateTime]
  );
}
