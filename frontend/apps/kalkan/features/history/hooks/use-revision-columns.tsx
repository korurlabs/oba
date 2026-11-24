"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import type { PageRevisionListItem } from "@/features/pages/schemas";

interface RevisionColumnsOptions {
  /** Shows a revision below the list. */
  onSelect: (revision: PageRevisionListItem) => void;
}

/**
 * The revision list: the version, its language, the title the save gave
 * (a button that shows the revision, so the keyboard reaches it), when and by
 * whom it was saved.
 */
export function useRevisionColumns({ onSelect }: RevisionColumnsOptions) {
  const t = useTranslations("history");
  const { formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<PageRevisionListItem, unknown>[]>(
    () => [
      {
        id: "version",
        header: t("columns.version"),
        size: 90,
        cell: ({ row }) => (
          <span className="text-foreground text-sm font-medium tabular-nums">
            {row.original.version}
          </span>
        ),
      },
      {
        id: "locale",
        header: t("columns.locale"),
        size: 110,
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm">
            {t(`locales.${row.original.locale}`)}
          </span>
        ),
      },
      {
        id: "title",
        header: t("columns.title"),
        meta: { flex: true },
        cell: ({ row }) => (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onSelect(row.original);
            }}
            className="text-foreground focus-visible:ring-ring block max-w-full truncate rounded-sm text-left text-sm hover:underline focus-visible:ring-2 focus-visible:outline-none"
          >
            {row.original.title}
          </button>
        ),
      },
      {
        id: "createdAt",
        header: t("columns.createdAt"),
        size: 170,
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm whitespace-nowrap tabular-nums">
            {formatDateTime(row.original.createdAt)}
          </span>
        ),
      },
      {
        id: "createdBy",
        header: t("columns.createdBy"),
        size: 170,
        cell: ({ row }) => (
          <span className="text-muted-foreground block truncate text-sm">
            {row.original.createdByName ?? t("unknownUser")}
          </span>
        ),
      },
    ],
    [t, formatDateTime, onSelect]
  );
}
