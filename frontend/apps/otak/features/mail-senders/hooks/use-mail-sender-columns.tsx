"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Button } from "@oba/ui/sky/button";
import type { MailSenderListItem } from "../schemas";

interface MailSenderColumnsOptions {
  /** Null hides the delete action: only an admin deletes a sender. */
  onDelete: ((mailSender: MailSenderListItem) => void) | null;
}

/**
 * The mail sender table: the address takes the free width, then its type and
 * the day it was added. The actions column is left out when there is no action to offer.
 */
export function useMailSenderColumns({ onDelete }: MailSenderColumnsOptions) {
  const t = useTranslations("mailSenders");
  const { formatDate, formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<MailSenderListItem, unknown>[]>(() => {
    const columns: ColumnDef<MailSenderListItem, unknown>[] = [
      {
        id: "mailAddress",
        accessorKey: "mailAddress",
        header: t("columns.mailAddress"),
        cell: ({ row }) => (
          <span
            className="text-foreground block truncate text-sm font-medium"
            title={row.original.mailAddress}
          >
            {row.original.mailAddress}
          </span>
        ),
        meta: { flex: true },
      },
      {
        id: "type",
        accessorKey: "type",
        header: t("columns.type"),
        size: 110,
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm whitespace-nowrap">
            {t(`types.${row.original.type}`)}
          </span>
        ),
      },
      {
        id: "createdAt",
        accessorKey: "createdAt",
        header: t("columns.createdAt"),
        size: 120,
        cell: ({ row }) => (
          <span
            className="text-muted-foreground text-sm whitespace-nowrap tabular-nums"
            title={formatDateTime(row.original.createdAt)}
          >
            {formatDate(row.original.createdAt)}
          </span>
        ),
      },
    ];

    if (onDelete) {
      columns.push({
        id: "actions",
        header: () => <span className="sr-only">{t("columns.actions")}</span>,
        size: 56,
        cell: ({ row }) => (
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t("delete", { mailAddress: row.original.mailAddress })}
              onClick={(event) => {
                event.stopPropagation();
                onDelete(row.original);
              }}
            >
              <Trash2 aria-hidden="true" />
            </Button>
          </div>
        ),
      });
    }

    return columns;
  }, [t, formatDate, formatDateTime, onDelete]);
}
