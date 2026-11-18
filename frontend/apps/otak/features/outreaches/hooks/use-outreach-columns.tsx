"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { CircleCheck, Eye, Pencil, Send, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { OutreachStatusBadge } from "../components/outreach-status-badge";
import {
  canCompleteOutreach,
  canSendOutreach,
  isOutreachEditable,
  type OutreachListItem,
} from "../schemas";

export interface OutreachRowActions {
  /** Opens the read-only view, with the mail and the attachments of a mail; every member may. */
  onView: (outreach: OutreachListItem) => void;
  /** Null hides the edit action: only an admin edits a draft. */
  onEdit: ((outreach: OutreachListItem) => void) | null;
  /** Null hides the send action: only an admin sends a mail. */
  onSend: ((outreach: OutreachListItem) => void) | null;
  /** Null hides the done action: only an admin marks a WhatsApp message or a call as done. */
  onComplete: ((outreach: OutreachListItem) => void) | null;
  /** Null hides the delete action: only an admin deletes. */
  onDelete: ((outreach: OutreachListItem) => void) | null;
}

interface OutreachColumnsOptions extends OutreachRowActions {
  /** Adds a column that links to the lead, for the organization's list. */
  showLead: boolean;
}

/**
 * The outreach table. The title takes the free width and opens the view;
 * then the channel, the contact, the status, when and from which mailbox it
 * was sent, and the last change. Edit, delete and either send (a mail) or
 * mark as done (a WhatsApp message or a call) show only on an outreach that
 * is not SENT yet, as the server refuses them afterwards.
 */
export function useOutreachColumns({
  showLead,
  onView,
  onEdit,
  onSend,
  onComplete,
  onDelete,
}: OutreachColumnsOptions) {
  const t = useTranslations("outreaches");
  const { formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<OutreachListItem, unknown>[]>(() => {
    // Send and done never show on the same row, so they share one slot.
    const actionCount = 1 + (onEdit ? 1 : 0) + (onSend || onComplete ? 1 : 0) + (onDelete ? 1 : 0);
    const columns: ColumnDef<OutreachListItem, unknown>[] = [
      {
        id: "title",
        accessorKey: "title",
        header: t("columns.title"),
        // A button, so the keyboard reaches the view; the row's click is for the mouse.
        cell: ({ row }) => (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onView(row.original);
            }}
            className="text-foreground block max-w-full truncate text-left text-sm font-medium hover:underline"
          >
            {row.original.title}
          </button>
        ),
        meta: { flex: true },
      },
    ];

    if (showLead) {
      columns.push({
        id: "lead",
        header: t("columns.lead"),
        size: 110,
        cell: ({ row }) => (
          <Link
            href={`/relations/${row.original.relationId}`}
            onClick={(event) => event.stopPropagation()}
            className="text-sm whitespace-nowrap underline-offset-4 hover:underline"
          >
            {t("openLead")}
          </Link>
        ),
      });
    }

    columns.push(
      {
        id: "type",
        accessorKey: "type",
        header: t("columns.type"),
        size: 100,
        cell: ({ row }) => (
          <span className="block truncate text-sm">{t(`types.${row.original.type}`)}</span>
        ),
      },
      {
        id: "contact",
        header: t("columns.contact"),
        size: 180,
        cell: ({ row }) =>
          row.original.contact ? (
            <span className="block truncate text-sm" title={row.original.contact.name}>
              {row.original.contact.name}
            </span>
          ) : (
            <span className="text-muted-foreground block truncate text-sm">
              {t("contactDeleted")}
            </span>
          ),
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("columns.status"),
        size: 100,
        cell: ({ row }) => <OutreachStatusBadge status={row.original.status} />,
      },
      {
        id: "sentAt",
        accessorKey: "sentAt",
        header: t("columns.sentAt"),
        size: 160,
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm whitespace-nowrap tabular-nums">
            {row.original.sentAt ? formatDateTime(row.original.sentAt) : t("notSent")}
          </span>
        ),
      },
      {
        id: "sender",
        accessorKey: "mailSenderAddress",
        header: t("columns.sender"),
        size: 180,
        cell: ({ row }) => (
          <span
            className="text-muted-foreground block truncate text-sm"
            title={row.original.mailSenderAddress ?? undefined}
          >
            {row.original.mailSenderAddress ?? t("noSender")}
          </span>
        ),
      },
      {
        id: "updatedAt",
        accessorKey: "updatedAt",
        header: t("columns.updatedAt"),
        size: 160,
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm whitespace-nowrap tabular-nums">
            {formatDateTime(row.original.updatedAt)}
          </span>
        ),
      },
      {
        id: "actions",
        header: () => <span className="sr-only">{t("columns.actions")}</span>,
        size: 16 + actionCount * 36,
        cell: ({ row }) => {
          const outreach = row.original;
          const editable = isOutreachEditable(outreach);
          return (
            <div className="flex justify-end gap-1">
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label={t("view", { title: outreach.title })}
                onClick={(event) => {
                  event.stopPropagation();
                  onView(outreach);
                }}
              >
                <Eye aria-hidden="true" />
              </Button>
              {onEdit && editable && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t("edit", { title: outreach.title })}
                  onClick={(event) => {
                    event.stopPropagation();
                    onEdit(outreach);
                  }}
                >
                  <Pencil aria-hidden="true" />
                </Button>
              )}
              {onSend && canSendOutreach(outreach) && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t("send", { title: outreach.title })}
                  onClick={(event) => {
                    event.stopPropagation();
                    onSend(outreach);
                  }}
                >
                  <Send aria-hidden="true" />
                </Button>
              )}
              {onComplete && canCompleteOutreach(outreach) && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t("complete", { title: outreach.title })}
                  onClick={(event) => {
                    event.stopPropagation();
                    onComplete(outreach);
                  }}
                >
                  <CircleCheck aria-hidden="true" />
                </Button>
              )}
              {onDelete && editable && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t("delete", { title: outreach.title })}
                  onClick={(event) => {
                    event.stopPropagation();
                    onDelete(outreach);
                  }}
                >
                  <Trash2 aria-hidden="true" />
                </Button>
              )}
            </div>
          );
        },
      }
    );

    return columns;
  }, [t, formatDateTime, showLead, onView, onEdit, onSend, onComplete, onDelete]);
}
