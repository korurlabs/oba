"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { AlertTriangle, RotateCcw } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Button } from "@oba/ui/sky/button";
import type { PageTrashItem } from "@/features/pages/schemas";
import { listedPageTitle } from "@/features/recent/lib/page-label";

interface TrashColumnsOptions {
  onRestore: (page: PageTrashItem) => void;
  /** The page whose restore is under way, so its button cannot be pressed twice. */
  restoringId: string | null;
  /** Pages whose restore was refused because their parent is in the trash too. */
  parentFirstIds: ReadonlySet<string>;
}

/**
 * The trash: the page and its path take the free width, then when and by
 * whom it was deleted, how many pages below it the same delete took, and the
 * restore action.
 */
export function useTrashColumns({ onRestore, restoringId, parentFirstIds }: TrashColumnsOptions) {
  const t = useTranslations("trash");
  const { formatDate, formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<PageTrashItem, unknown>[]>(
    () => [
      {
        id: "title",
        header: t("columns.title"),
        cell: ({ row }) => (
          <div className="flex min-w-0 flex-col gap-0.5">
            <span className="text-foreground block truncate text-sm font-medium">
              {listedPageTitle(row.original)}
            </span>
            <span className="text-muted-foreground block truncate text-xs">
              {row.original.path}
            </span>
            {parentFirstIds.has(row.original.id) && (
              <span
                role="status"
                className="text-warning-text flex items-start gap-1.5 text-xs whitespace-normal"
              >
                <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                {t("parentFirst")}
              </span>
            )}
          </div>
        ),
        meta: { flex: true },
      },
      {
        id: "deletedAt",
        header: t("columns.deletedAt"),
        size: 130,
        cell: ({ row }) => (
          <span
            className="text-muted-foreground text-sm whitespace-nowrap tabular-nums"
            title={formatDateTime(row.original.deletedAt)}
          >
            {formatDate(row.original.deletedAt)}
          </span>
        ),
      },
      {
        id: "deletedBy",
        header: t("columns.deletedBy"),
        size: 170,
        cell: ({ row }) => (
          <span className="text-muted-foreground block truncate text-sm">
            {row.original.deletedByName ?? t("unknownUser")}
          </span>
        ),
      },
      {
        id: "descendants",
        header: t("columns.descendants"),
        size: 130,
        meta: { align: "right" },
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm tabular-nums">
            {row.original.descendantCount}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="sr-only">{t("columns.actions")}</span>,
        size: 130,
        meta: { align: "right" },
        cell: ({ row }) => (
          <Button
            variant="outline"
            size="sm"
            disabled={restoringId === row.original.id}
            aria-label={t("restoreLabel", { title: listedPageTitle(row.original) })}
            onClick={() => onRestore(row.original)}
          >
            <RotateCcw aria-hidden="true" />
            {t("restore")}
          </Button>
        ),
      },
    ],
    [t, formatDate, formatDateTime, onRestore, restoringId, parentFirstIds]
  );
}
