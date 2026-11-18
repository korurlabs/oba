"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Eye, Pencil, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Button } from "@oba/ui/sky/button";
import type { MailTemplateListItem } from "../schemas";

interface MailTemplateColumnsOptions {
  /** Opens the read-only view with the subject and body; every member may. */
  onPreview: (template: MailTemplateListItem) => void;
  /** Null makes the table read-only: only an admin edits a mail template. */
  onEdit: ((template: MailTemplateListItem) => void) | null;
  /** Null hides the delete action: only an admin deletes a mail template. */
  onDelete: ((template: MailTemplateListItem) => void) | null;
}

/**
 * The mail template table. The name takes the free width and opens the edit
 * dialog for an admin and the preview for a member; then the subject, the
 * language and the last change.
 */
export function useMailTemplateColumns({
  onPreview,
  onEdit,
  onDelete,
}: MailTemplateColumnsOptions) {
  const t = useTranslations("mailTemplates");
  const tTemplates = useTranslations("documentTemplates");
  const { formatDate, formatDateTime, dayKey, relativeDay } = useDateFormat();

  return useMemo<ColumnDef<MailTemplateListItem, unknown>[]>(() => {
    const open = onEdit ?? onPreview;
    const actionCount = 1 + (onEdit ? 1 : 0) + (onDelete ? 1 : 0);
    return [
      {
        id: "name",
        accessorKey: "name",
        header: t("columns.name"),
        size: 220,
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
      },
      {
        id: "subject",
        accessorKey: "subject",
        header: t("columns.subject"),
        cell: ({ row }) => (
          <span
            className="text-muted-foreground block truncate text-sm"
            title={row.original.subject}
          >
            {row.original.subject}
          </span>
        ),
        meta: { flex: true },
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
        size: 120,
        cell: ({ row }) => {
          const recent = relativeDay(dayKey(row.original.updatedAt));
          return (
            <span
              className="text-muted-foreground text-sm whitespace-nowrap tabular-nums"
              title={formatDateTime(row.original.updatedAt)}
            >
              {recent ? tTemplates(`updated.${recent}`) : formatDate(row.original.updatedAt)}
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
  }, [t, tTemplates, formatDate, formatDateTime, dayKey, relativeDay, onPreview, onEdit, onDelete]);
}
