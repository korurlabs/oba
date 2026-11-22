"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Eye, Pencil, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import type { DocumentTemplateListItem } from "../schemas";

interface DocumentTemplateColumnsOptions {
  /** Opens the read-only view with the PDF; every member may. */
  onPreview: (template: DocumentTemplateListItem) => void;
  /** Null makes the table read-only: only an admin edits a template. */
  onEdit: ((template: DocumentTemplateListItem) => void) | null;
  /** Null hides the delete action: only an admin deletes a template. */
  onDelete: ((template: DocumentTemplateListItem) => void) | null;
}

/**
 * The template table. The name takes the free width and opens the edit
 * dialog for an admin and the preview for a member; then the type, the
 * language, the legal entity it is bound to ("All" for every legal entity),
 * whether the template can be used, and the last change.
 */
export function useDocumentTemplateColumns({
  onPreview,
  onEdit,
  onDelete,
}: DocumentTemplateColumnsOptions) {
  const t = useTranslations("documentTemplates");
  const { formatDate, formatDateTime, dayKey, relativeDay } = useDateFormat();

  return useMemo<ColumnDef<DocumentTemplateListItem, unknown>[]>(() => {
    const open = onEdit ?? onPreview;
    const actionCount = 1 + (onEdit ? 1 : 0) + (onDelete ? 1 : 0);
    return [
      {
        id: "name",
        accessorKey: "name",
        header: t("columns.name"),
        // A button, so the keyboard reaches the dialog; the row's click is for the mouse.
        cell: ({ row }) => (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              open(row.original);
            }}
            className="text-foreground block max-w-full truncate text-left text-sm font-medium hover:underline"
          >
            {row.original.name}
          </button>
        ),
        meta: { flex: true },
      },
      {
        id: "type",
        accessorKey: "type",
        header: t("columns.type"),
        size: 140,
        cell: ({ row }) => (
          <span className="block truncate text-sm">{t(`types.${row.original.type}`)}</span>
        ),
      },
      {
        id: "language",
        accessorKey: "language",
        header: t("columns.language"),
        size: 110,
        cell: ({ row }) => (
          <span className="text-muted-foreground block truncate text-sm">
            {t(`languages.${row.original.language}`)}
          </span>
        ),
      },
      {
        id: "legalEntity",
        accessorKey: "legalEntityShortName",
        header: t("columns.legalEntity"),
        size: 150,
        cell: ({ row }) =>
          row.original.legalEntityShortName ? (
            <span className="block truncate text-sm">{row.original.legalEntityShortName}</span>
          ) : (
            <span className="text-muted-foreground block truncate text-sm">
              {t("legalEntityAll")}
            </span>
          ),
      },
      {
        id: "ready",
        accessorKey: "ready",
        header: t("columns.ready"),
        size: 130,
        cell: ({ row }) =>
          row.original.ready ? (
            <Badge variant="success">{t("ready.yes")}</Badge>
          ) : (
            <Badge variant="warning">{t("ready.no")}</Badge>
          ),
      },
      {
        id: "updatedAt",
        accessorKey: "updatedAt",
        header: t("columns.updatedAt"),
        size: 120,
        cell: ({ row }) => {
          const recent = relativeDay(dayKey(row.original.updatedAt));
          return (
            <span
              className="text-muted-foreground text-sm whitespace-nowrap tabular-nums"
              title={formatDateTime(row.original.updatedAt)}
            >
              {recent ? t(`updated.${recent}`) : formatDate(row.original.updatedAt)}
            </span>
          );
        },
      },
      {
        id: "actions",
        header: () => <span className="sr-only">{t("columns.actions")}</span>,
        size: 24 + actionCount * 32,
        cell: ({ row }) => (
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t("preview", { name: row.original.name })}
              onClick={(event) => {
                event.stopPropagation();
                onPreview(row.original);
              }}
            >
              <Eye aria-hidden="true" />
            </Button>
            {onEdit && (
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
            )}
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
  }, [t, formatDate, formatDateTime, dayKey, relativeDay, onPreview, onEdit, onDelete]);
}
