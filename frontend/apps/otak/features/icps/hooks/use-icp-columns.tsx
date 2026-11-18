"use client";

import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Pencil, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Button } from "@oba/ui/sky/button";
import type { RelationSize } from "@/features/relations/schemas";
import type { IcpListItem } from "../schemas";

interface IcpColumnsOptions {
  onEdit: (icp: IcpListItem) => void;
  /** Null hides the delete action: only an admin may delete. */
  onDelete: ((icp: IcpListItem) => void) | null;
}

/**
 * The ICP table. The name takes the free width and opens the edit dialog;
 * the other columns have fixed widths. A missing country reads "Any", and
 * the size range names only the bounds that are set.
 */
export function useIcpColumns({ onEdit, onDelete }: IcpColumnsOptions) {
  const t = useTranslations("icps");
  const locale = useLocale();
  const { formatDateTime } = useDateFormat();
  const countryNames = useMemo(() => new Intl.DisplayNames([locale], { type: "region" }), [locale]);

  return useMemo<ColumnDef<IcpListItem, unknown>[]>(() => {
    const sizeRange = (min: RelationSize | null, max: RelationSize | null): string => {
      if (min && max)
        return t("sizeRange.between", { min: t(`sizeBands.${min}`), max: t(`sizeBands.${max}`) });
      if (min) return t("sizeRange.from", { min: t(`sizeBands.${min}`) });
      if (max) return t("sizeRange.upTo", { max: t(`sizeBands.${max}`) });
      return t("sizeRange.any");
    };

    return [
      {
        id: "name",
        accessorKey: "name",
        header: t("columns.name"),
        // A button, so the keyboard reaches the edit dialog; the row's click is for the mouse.
        cell: ({ row }) => (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onEdit(row.original);
            }}
            className="text-foreground block max-w-full truncate text-left text-sm font-medium hover:underline"
          >
            {row.original.name}
          </button>
        ),
        meta: { flex: true },
      },
      {
        id: "industry",
        accessorKey: "industry",
        header: t("columns.industry"),
        size: 170,
        cell: ({ row }) => (
          <span className="block truncate text-sm">{t(`industries.${row.original.industry}`)}</span>
        ),
      },
      {
        id: "country",
        accessorKey: "country",
        header: t("columns.country"),
        size: 160,
        cell: ({ row }) =>
          row.original.country ? (
            <span className="block truncate text-sm">
              {countryNames.of(row.original.country) ?? row.original.country}
            </span>
          ) : (
            <span className="text-muted-foreground text-sm italic">{t("anyCountry")}</span>
          ),
      },
      {
        id: "size",
        header: t("columns.size"),
        size: 170,
        cell: ({ row }) => (
          <span className="block truncate text-sm">
            {sizeRange(row.original.companySizeMin, row.original.companySizeMax)}
          </span>
        ),
      },
      {
        id: "targetPersonRole",
        accessorKey: "targetPersonRole",
        header: t("columns.targetPersonRole"),
        size: 150,
        cell: ({ row }) =>
          row.original.targetPersonRole ? (
            <span className="block truncate text-sm">
              {t(`roles.${row.original.targetPersonRole}`)}
            </span>
          ) : (
            <span className="text-muted-foreground text-sm">
              <span aria-hidden="true">-</span>
              <span className="sr-only">{t("noRole")}</span>
            </span>
          ),
      },
      {
        id: "updatedAt",
        accessorKey: "updatedAt",
        header: t("columns.updatedAt"),
        size: 170,
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm whitespace-nowrap tabular-nums">
            {formatDateTime(row.original.updatedAt)}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="sr-only">{t("columns.actions")}</span>,
        size: onDelete ? 88 : 56,
        cell: ({ row }) => (
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t("edit", { name: row.original.name })}
              onClick={(event) => {
                event.stopPropagation();
                onEdit(row.original);
              }}
            >
              <Pencil aria-hidden="true" />
            </Button>
            {onDelete && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("delete", { name: row.original.name })}
                onClick={(event) => {
                  event.stopPropagation();
                  onDelete(row.original);
                }}
              >
                <Trash2 aria-hidden="true" />
              </Button>
            )}
          </div>
        ),
      },
    ];
  }, [t, countryNames, formatDateTime, onEdit, onDelete]);
}
