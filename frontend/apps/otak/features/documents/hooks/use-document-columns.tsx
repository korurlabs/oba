"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link } from "@oba/shared/libs/i18n/navigation";
import type { DocumentListItem } from "../schemas";
import { useEditedText } from "./use-edited-text";

/**
 * The document table. The title takes the free width and links to the
 * document page; then the relation, the legal entity that issues it, the
 * type, the language, the last change and who made it, and how many times a
 * PDF copy of it went out with a mail.
 */
export function useDocumentColumns() {
  const t = useTranslations("documents");
  const tTemplates = useTranslations("documentTemplates");
  const { formatDateTime } = useDateFormat();
  const editedText = useEditedText();

  return useMemo<ColumnDef<DocumentListItem, unknown>[]>(
    () => [
      {
        id: "title",
        accessorKey: "title",
        header: t("columns.title"),
        // A link, so the keyboard reaches the page; the row's click is for the mouse.
        cell: ({ row }) => (
          <Link
            href={`/documents/${row.original.id}`}
            onClick={(event) => event.stopPropagation()}
            className="text-foreground block max-w-full truncate text-sm font-medium hover:underline"
          >
            {row.original.title}
          </Link>
        ),
        meta: { flex: true },
      },
      {
        id: "relation",
        accessorKey: "relationName",
        header: t("columns.relation"),
        size: 180,
        cell: ({ row }) => (
          <span className="block truncate text-sm">
            {row.original.relationName ?? (
              <span className="text-muted-foreground">{t("noRelation")}</span>
            )}
          </span>
        ),
      },
      {
        id: "legalEntity",
        accessorKey: "legalEntityShortName",
        header: t("columns.legalEntity"),
        size: 160,
        cell: ({ row }) => (
          <span className="block truncate text-sm">{row.original.legalEntityShortName}</span>
        ),
      },
      {
        id: "type",
        accessorKey: "type",
        header: t("columns.type"),
        size: 130,
        cell: ({ row }) => (
          <span className="block truncate text-sm">{tTemplates(`types.${row.original.type}`)}</span>
        ),
      },
      {
        id: "language",
        accessorKey: "language",
        header: t("columns.language"),
        size: 110,
        cell: ({ row }) => (
          <span className="text-muted-foreground block truncate text-sm">
            {tTemplates(`languages.${row.original.language}`)}
          </span>
        ),
      },
      {
        id: "updatedAt",
        accessorKey: "updatedAt",
        header: t("columns.updatedAt"),
        size: 190,
        cell: ({ row }) => (
          <span
            className="text-muted-foreground block truncate text-sm tabular-nums"
            title={formatDateTime(row.original.updatedAt)}
          >
            {editedText(row.original.updatedAt, row.original.updatedBy)}
          </span>
        ),
      },
      {
        id: "snapshotCount",
        accessorKey: "snapshotCount",
        header: t("columns.snapshotCount"),
        size: 90,
        meta: { align: "right" },
        cell: ({ row }) => (
          <span className="block text-right text-sm tabular-nums">
            {row.original.snapshotCount}
          </span>
        ),
      },
    ],
    [t, tTemplates, formatDateTime, editedText]
  );
}
